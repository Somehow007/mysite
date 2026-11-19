package io.github.somehow.mysite.ragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeChunkMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import io.github.somehow.mysite.ragent.chunking.DocumentChunker;
import io.github.somehow.mysite.ragent.llm.embedding.EmbeddingService;
import io.github.somehow.mysite.ragent.usage.UsageContext;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档管理服务 —— 文章 → 分块 → 向量化 → 入库 的完整流程。
 *
 * 失败处理：
 *   全程 try/catch，embedding API 一旦失败，文档记录不会永远停在 CHUNKING 状态。
 *   标记 FAILED + 记录 fail_reason，Dashboard 文档列表可直观看到失败并手动重试。
 *   重试 = 再次调用本方法，开头的"删旧档"逻辑保证幂等，
 *   建表 SQL 里的 uk_doc_source 唯一约束兜底并发重复插入。
 *
 * 多 KB 同步（方案 A）：同一篇文章在 K 个知识库的 chunk 文本、embedding 文本、
 * 向量本体逐比特相同（分块配置全局 rag.chunk.*，embedding 为全局单例；
 * t_knowledge_base 的 chunk/embedding 配置列是死字段，不进管线）。
 * 因此 ingestArticle 先逐 KB 建档，再只分块一次、embedding 一次，
 * 最后逐 KB 写 chunk/vector —— embedding 计费从 K×C 降为真实的 uniqTexts 批次数。
 * 注意不变量：若未来 t_knowledge_base 真的做 per-KB 分块/embedding 配置，
 * 「分块一次」假设失效，需回退为逐 KB chunk。
 */
@Slf4j
@Service
public class KnowledgeDocumentService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public KnowledgeDocumentService(KnowledgeBaseMapper kbMapper,
                                     KnowledgeDocumentMapper docMapper,
                                     KnowledgeChunkMapper chunkMapper,
                                     DocumentChunker chunker,
                                     EmbeddingService embeddingService,
                                     VectorStore vectorStore) {
        this.kbMapper = kbMapper;
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 文章 → 所有启用的知识库同步（异步执行，不阻塞文章发布）。
     * <p>
     * 业内实践：Dify/FastGPT 等系统不设"默认 KB"——每个数据源显式绑定到 KB。
     * 博客场景更简单：直接同步到所有启用 KB，保证每篇新文章在所有 KB 中可检索。
     */
    @Async("ragAsyncExecutor")
    public void syncArticle(ArticleDO article) {
        // 公开性门：草稿/私有文章不入库。取消发布或转私有时走 removeArticle 清掉已入库向量，
        // 否则旧分块仍可经 AI 问答泄漏非公开内容。
        if (!isPubliclySyncable(article)) {
            removeArticle(article.getId());
            return;
        }
        List<KnowledgeBaseDO> enabledKbs = kbMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getEnabled, true)
                .or().isNull(KnowledgeBaseDO::getEnabled));  // 兼容旧数据 enabled=NULL
        if (enabledKbs.isEmpty()) {
            log.warn("没有启用的知识库，跳过文章同步: articleId={}", article.getId());
            return;
        }
        ingestArticle(article, enabledKbs);
    }

    /**
     * 文章 → 指定知识库同步（异步执行）。
     *
     * 幂等策略：先查是否已有该文章的文档记录 → 有则删除旧的向量/分块/文档，
     * 再创建新记录。uk_doc_source 唯一约束兜底并发的重复插入。
     * 显式重建语义（addArticles / syncKb / reprocessDoc），不过任何内容门禁。
     */
    @Async("ragAsyncExecutor")
    public void syncArticle(ArticleDO article, Long kbId) {
        // 公开性门（与 1-arg 入口同规则）：草稿/私有文章不同步，防止经 KB 管理接口绕过
        if (!isPubliclySyncable(article)) {
            log.debug("跳过非公开文章同步: articleId={}, published={}, visibility={}",
                article.getId(), article.getPublished(), article.getVisibility());
            return;
        }
        KnowledgeBaseDO kb = kbMapper.selectById(kbId);
        if (kb == null) {
            log.warn("知识库不存在, kbId={}", kbId);
            return;
        }
        ingestArticle(article, List.of(kb));
    }

    /**
     * 文章 → 多知识库入库核心（私有，同步线程内串行执行）。
     * <p>
     * 三段式：
     *   Phase A 逐 KB 隔离 —— 删旧档 + 建 PENDING 文档，单 KB 失败不影响其他 KB；
     *   Phase B 一次 —— 分块一次 + embedding 文本去重后 embedBatch 一次，
     *     任一异常 → 所有已建档文档标 FAILED（失败可见、可 reprocessDoc 重试）；
     *   Phase C 逐 KB 隔离 —— 写 chunk + 批量 insert 向量 + 对齐模型名 + READY，
     *     单 KB 写库失败仅该文档 FAILED，孤儿 chunk 行下次同步 deleteByDocId 自愈。
     */
    private void ingestArticle(ArticleDO article, List<KnowledgeBaseDO> kbs) {
        UsageContext.open(UsageContext.State.builder()
            .traceId(UUID.randomUUID().toString())
            .callType("EMBED")
            .build());
        try {
            // Phase A：逐 KB 建档
            List<KbDocPair> pairs = new ArrayList<>(kbs.size());
            for (KnowledgeBaseDO kb : kbs) {
                try {
                    KnowledgeDocumentDO existingDoc = docMapper.findBySourceRef(
                        kb.getId(), "ARTICLE", article.getId().toString());
                    if (existingDoc != null) {
                        vectorStore.deleteByDocId(existingDoc.getId());
                        chunkMapper.deleteByDocId(existingDoc.getId());
                        docMapper.deleteById(existingDoc.getId());
                    }
                    KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
                    doc.setKbId(kb.getId());
                    doc.setTitle(article.getTitle());
                    doc.setSourceType("ARTICLE");
                    doc.setSourceRef(article.getId().toString());
                    doc.setFileType("MD");
                    doc.setStatus("PENDING");
                    docMapper.insert(doc);
                    pairs.add(new KbDocPair(kb, doc));
                } catch (Exception e) {
                    log.error("知识库建档失败, kbId={}, articleId={}", kb.getId(), article.getId(), e);
                }
            }
            if (pairs.isEmpty()) {
                return;
            }
            UsageContext.setDocumentId(pairs.get(0).doc().getId());

            // Phase B：分块一次 + embedding 一次（chunk/embed 任一失败 → 全部已建档 FAILED）
            String content = article.getContent();
            List<DocumentChunker.Chunk> chunks;
            List<float[]> embeddings;
            try {
                // docId/kbId 仅占位：Chunk 上的这两个字段写库时不被消费，
                // chunkDO/vectorEntry 均用各 KB 自己的 doc/kb id
                KbDocPair first = pairs.get(0);
                chunks = chunker.chunk(content, first.doc().getId(), first.kb().getId());
                for (KbDocPair pair : pairs) {
                    pair.doc().setChunkCount(chunks.size());
                    pair.doc().setCharCount(content.length());
                    pair.doc().setStatus("CHUNKING");
                    docMapper.updateById(pair.doc());
                }

                // embeddingText 优先（Ragent 模式），null 时回退到 content；
                // 文本去重后一次 embedBatch，结果按原序扩回 —— 同一文章跨 chunk
                // 乃至跨 KB 的重复文本只向量化一次
                List<String> chunkTexts = chunks.stream()
                    .map(c -> c.embeddingText() != null ? c.embeddingText() : c.content())
                    .toList();
                Map<String, Integer> uniqIndex = new LinkedHashMap<>();
                for (String text : chunkTexts) {
                    uniqIndex.putIfAbsent(text, uniqIndex.size());
                }
                List<String> uniqTexts = List.copyOf(uniqIndex.keySet());
                List<float[]> uniqEmbeddings = embeddingService.embedBatch(uniqTexts);
                if (uniqEmbeddings.size() != uniqTexts.size()) {
                    throw new IllegalStateException("embedding 数量不匹配: expected="
                        + uniqTexts.size() + ", actual=" + uniqEmbeddings.size());
                }
                embeddings = new ArrayList<>(chunkTexts.size());
                for (String text : chunkTexts) {
                    embeddings.add(uniqEmbeddings.get(uniqIndex.get(text)));
                }
            } catch (Exception e) {
                log.error("文章分块/向量化失败, articleId={}", article.getId(), e);
                for (KbDocPair pair : pairs) {
                    markFailed(pair.doc(), e);
                }
                return;
            }

            // Phase C：逐 KB 写 chunk + 批量向量 + READY
            for (KbDocPair pair : pairs) {
                try {
                    List<VectorStore.VectorEntry> entries = new ArrayList<>(chunks.size());
                    for (int i = 0; i < chunks.size(); i++) {
                        DocumentChunker.Chunk c = chunks.get(i);
                        KnowledgeChunkDO chunkDO = new KnowledgeChunkDO();
                        chunkDO.setDocId(pair.doc().getId());
                        chunkDO.setKbId(pair.kb().getId());
                        chunkDO.setChunkIndex(c.index());
                        chunkDO.setContent(c.content());
                        chunkDO.setEmbeddingText(c.embeddingText());
                        chunkDO.setCharCount(c.content().length());
                        chunkMapper.insert(chunkDO);
                        entries.add(new VectorStore.VectorEntry(
                            chunkDO.getId(), pair.kb().getId(), embeddings.get(i),
                            embeddingModelName(pair.kb())));
                    }
                    if (!entries.isEmpty()) {
                        vectorStore.insert(entries);
                    }

                    // 把知识库上的模型名同步成实际入库用的模型
                    String usedModel = embeddingModelName(pair.kb());
                    if (usedModel != null && !usedModel.equals(pair.kb().getEmbeddingModel())) {
                        pair.kb().setEmbeddingModel(usedModel);
                        kbMapper.updateById(pair.kb());
                    }
                    pair.doc().setStatus("READY");
                    docMapper.updateById(pair.doc());
                } catch (Exception e) {
                    log.error("知识库向量写入失败, kbId={}, articleId={}",
                        pair.kb().getId(), article.getId(), e);
                    markFailed(pair.doc(), e);
                }
            }
            log.info("文章向量化完成: articleId={}, title={}, kbs={}, chunks={}",
                article.getId(), article.getTitle(), pairs.size(), chunks.size());
        } catch (Exception e) {
            log.error("文章向量化失败, articleId={}", article.getId(), e);
        } finally {
            UsageContext.close();
        }
    }

    /** 标记文档 FAILED + 记录原因；标记本身失败只记日志，不影响其他 KB。 */
    private void markFailed(KnowledgeDocumentDO doc, Exception e) {
        try {
            doc.setStatus("FAILED");
            doc.setFailReason(e.getClass().getSimpleName() + ": " + e.getMessage());
            docMapper.updateById(doc);
        } catch (Exception ex) {
            log.error("标记文档 FAILED 失败, docId={}", doc.getId(), ex);
        }
    }

    /**
     * 文章删除 → 清理所有知识库中对应的文档/分块/向量（异步执行，不阻塞文章删除）。
     *
     * 清理顺序与 {@link #ingestArticle(ArticleDO, List)} 的"删旧档"逻辑一致：
     * 先删向量，再删分块，最后删文档记录。
     * 全程 try/catch 兜底：RAG 清理失败不能影响文章删除主流程，只记错误日志。
     */
    @Async("ragAsyncExecutor")
    public void removeArticle(Long articleId) {
        try {
            List<KnowledgeDocumentDO> docs = docMapper.findBySource("ARTICLE", articleId.toString());
            for (KnowledgeDocumentDO doc : docs) {
                vectorStore.deleteByDocId(doc.getId());
                chunkMapper.deleteByDocId(doc.getId());
                docMapper.deleteById(doc.getId());
            }
            if (!docs.isEmpty()) {
                log.info("文章 RAG 数据清理完成: articleId={}, 清理文档数={}", articleId, docs.size());
            }
        } catch (Exception e) {
            log.error("清理文章 RAG 数据失败, articleId={}", articleId, e);
        }
    }

    /** 仅已发布且公开的文章允许进入知识库（草稿/私有内容对聊天用户不可见） */
    private boolean isPubliclySyncable(ArticleDO article) {
        return Integer.valueOf(1).equals(article.getPublished())
            && (article.getVisibility() == null || article.getVisibility() == 0);
    }

    /** 入库向量必须标运行时模型名，否则改模型后检索会拿新旧向量交叉比较。 */
    private String embeddingModelName(KnowledgeBaseDO kb) {
        String runtime = embeddingService.currentModel();
        if (runtime != null && !runtime.isBlank()) {
            return runtime;
        }
        return kb.getEmbeddingModel();
    }

    /** Phase A 产物：知识库 + 其新建文档记录的配对。 */
    private record KbDocPair(KnowledgeBaseDO kb, KnowledgeDocumentDO doc) {}
}
