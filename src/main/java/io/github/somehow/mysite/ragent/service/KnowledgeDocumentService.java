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
import io.github.somehow.mysite.ragent.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档管理服务 —— 文章 → 分块 → 向量化 → 入库 的完整流程。
 *
 * 失败处理：
 *   全程 try/catch，embedding API 一旦失败，文档记录不会永远停在 CHUNKING 状态。
 *   标记 FAILED + 记录 fail_reason，Dashboard 文档列表可直观看到失败并手动重试。
 *   重试 = 再次调用本方法，开头的"删旧档"逻辑保证幂等，
 *   建表 SQL 里的 uk_doc_source 唯一约束兜底并发重复插入。
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
        List<KnowledgeBaseDO> enabledKbs = kbMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getEnabled, true)
                .or().isNull(KnowledgeBaseDO::getEnabled));  // 兼容旧数据 enabled=NULL
        if (enabledKbs.isEmpty()) {
            log.warn("没有启用的知识库，跳过文章同步: articleId={}", article.getId());
            return;
        }
        for (KnowledgeBaseDO kb : enabledKbs) {
            syncArticle(article, kb.getId());
        }
    }

    /**
     * 文章 → 指定知识库同步（异步执行）。
     *
     * 幂等策略：先查是否已有该文章的文档记录 → 有则删除旧的向量/分块/文档，
     * 再创建新记录。uk_doc_source 唯一约束兜底并发的重复插入。
     */
    @Async("ragAsyncExecutor")
    public void syncArticle(ArticleDO article, Long kbId) {
        KnowledgeDocumentDO doc = null;
        try {
            KnowledgeBaseDO kb = kbMapper.selectById(kbId);
            if (kb == null) {
                log.warn("知识库不存在, kbId={}", kbId);
                return;
            }

            // 2. 检查是否已有该文章的文档记录 → 有则删除旧的向量/分块/文档
            KnowledgeDocumentDO existingDoc = docMapper.findBySourceRef(
                kb.getId(), "ARTICLE", article.getId().toString());
            if (existingDoc != null) {
                vectorStore.deleteByDocId(existingDoc.getId());
                chunkMapper.deleteByDocId(existingDoc.getId());
                docMapper.deleteById(existingDoc.getId());
            }

            // 3. 创建新文档记录
            doc = new KnowledgeDocumentDO();
            doc.setKbId(kb.getId());
            doc.setTitle(article.getTitle());
            doc.setSourceType("ARTICLE");
            doc.setSourceRef(article.getId().toString());
            doc.setFileType("MD");
            doc.setStatus("PENDING");
            docMapper.insert(doc);

            // 4. 分块
            String content = article.getContent();
            List<DocumentChunker.Chunk> chunks = chunker.chunk(content, doc.getId(), kb.getId());
            doc.setChunkCount(chunks.size());
            doc.setCharCount(content.length());
            doc.setStatus("CHUNKING");
            docMapper.updateById(doc);

            // 5. 批量嵌入 — embeddingText 优先（Ragent 模式），null 时回退到 content
            List<String> chunkTexts = chunks.stream()
                .map(c -> c.embeddingText() != null ? c.embeddingText() : c.content())
                .toList();
            List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);

            // 6. 存储 chunks + vectors
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunker.Chunk c = chunks.get(i);
                KnowledgeChunkDO chunkDO = new KnowledgeChunkDO();
                chunkDO.setDocId(doc.getId());
                chunkDO.setKbId(kb.getId());
                chunkDO.setChunkIndex(c.index());
                chunkDO.setContent(c.content());
                chunkDO.setEmbeddingText(c.embeddingText());
                chunkDO.setCharCount(c.content().length());
                chunkMapper.insert(chunkDO);

                vectorStore.insert(List.of(new VectorStore.VectorEntry(
                    chunkDO.getId(), kb.getId(), embeddings.get(i), kb.getEmbeddingModel()
                )));
            }

            // 7. 标记完成
            doc.setStatus("READY");
            docMapper.updateById(doc);
            log.info("文章向量化完成: articleId={}, title={}, chunks={}",
                article.getId(), article.getTitle(), chunks.size());

        } catch (Exception e) {
            // 任何一步失败：标记 FAILED + 记录原因，等下次同步或人工重试
            log.error("文章向量化失败, articleId={}", article.getId(), e);
            if (doc != null && doc.getId() != null) {
                doc.setStatus("FAILED");
                doc.setFailReason(e.getClass().getSimpleName() + ": " + e.getMessage());
                docMapper.updateById(doc);
            }
        }
    }

    /**
     * 文章删除 → 清理所有知识库中对应的文档/分块/向量（异步执行，不阻塞文章删除）。
     *
     * 清理顺序与 {@link #syncArticle(ArticleDO, Long)} 的"删旧档"逻辑一致：
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
}
