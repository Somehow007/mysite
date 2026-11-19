package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeChunkMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import io.github.somehow.mysite.ragent.chunking.DocumentChunker;
import io.github.somehow.mysite.ragent.chunking.MarkdownChunker;
import io.github.somehow.mysite.ragent.llm.embedding.EmbeddingService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KnowledgeDocumentService 单元测试（Mock 所有外部依赖）。
 *
 * 覆盖：
 *   - 正常流程：文章 → 分块 → embedding → 入库 → READY
 *   - 幂等：重复同步删旧建新
 *   - 失败：embedding API 异常 → FAILED + fail_reason
 *   - 没有启用的知识库 → 跳过同步
 */
@DisplayName("KnowledgeDocumentService")
class KnowledgeDocumentServiceTest {

    private KnowledgeBaseMapper kbMapper;
    private KnowledgeDocumentMapper docMapper;
    private KnowledgeChunkMapper chunkMapper;
    private DocumentChunker chunker;
    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private KnowledgeDocumentService service;
    private KnowledgeBaseDO defaultKb;

    @BeforeEach
    void setUp() {
        kbMapper = mock(KnowledgeBaseMapper.class);
        docMapper = mock(KnowledgeDocumentMapper.class);
        chunkMapper = mock(KnowledgeChunkMapper.class);
        embeddingService = mock(EmbeddingService.class);
        vectorStore = mock(VectorStore.class);

        RagProperties props = new RagProperties();
        props.getChunk().setSize(800);
        props.getChunk().setOverlap(100);
        props.getChunk().setMaxChunksPerDoc(50);
        chunker = new MarkdownChunker(props);

        defaultKb = new KnowledgeBaseDO();
        defaultKb.setId(1L);
        defaultKb.setName("默认知识库");
        defaultKb.setCollectionName("default");
        defaultKb.setEmbeddingModel("text-embedding-v4");
        defaultKb.setEmbeddingDimension(1024);
        defaultKb.setChunkSize(800);
        defaultKb.setChunkOverlap(100);

        // 默认：返回默认知识库
        when(kbMapper.selectList(any())).thenReturn(List.of(defaultKb));
        // syncArticle(article, kbId) 会先通过 selectById 加载知识库
        when(kbMapper.selectById(anyLong())).thenReturn(defaultKb);

        // embedding → 1024 维全 0.5
        when(embeddingService.embedBatch(anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> {
                float[] v = new float[1024];
                for (int i = 0; i < 1024; i++) v[i] = 0.5f;
                return v;
            }).toList();
        });

        // 模拟 MyBatis-Plus IdWorker：insert 时自动赋 ID
        doAnswer(inv -> {
            KnowledgeDocumentDO doc = inv.getArgument(0);
            if (doc.getId() == null) doc.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            return 1;
        }).when(docMapper).insert(any(KnowledgeDocumentDO.class));

        doAnswer(inv -> {
            KnowledgeChunkDO chunk = inv.getArgument(0);
            if (chunk.getId() == null) chunk.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            return 1;
        }).when(chunkMapper).insert(any(KnowledgeChunkDO.class));

        service = new KnowledgeDocumentService(
            kbMapper, docMapper, chunkMapper, chunker, embeddingService, vectorStore);
    }

    @Nested
    @DisplayName("正常流程")
    class HappyPath {

        @Test
        @DisplayName("入库向量使用运行时 embedding 模型名，而不是知识库上的旧值")
        void shouldPersistRuntimeEmbeddingModel() {
            when(embeddingService.currentModel()).thenReturn("qwen3.7-text-embedding");

            ArticleDO article = new ArticleDO();
            article.setId(110L);
            article.setPublished(1);
            article.setTitle("向量模型对齐");
            article.setContent("## 模型\n\n查询向量与库存向量必须同一模型。");
            when(docMapper.findBySourceRef(1L, "ARTICLE", "110")).thenReturn(null);

            service.syncArticle(article);

            ArgumentCaptor<List<VectorStore.VectorEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore, atLeastOnce()).insert(captor.capture());
            assertTrue(captor.getAllValues().stream()
                .flatMap(List::stream)
                .allMatch(e -> "qwen3.7-text-embedding".equals(e.model())));
            verify(kbMapper).updateById(argThat((KnowledgeBaseDO kb) ->
                "qwen3.7-text-embedding".equals(kb.getEmbeddingModel())));
        }

        @Test
        @DisplayName("新文章 → 分块 → embedding → 入库 → READY")
        void newArticleShouldCompleteFullPipeline() {
            ArticleDO article = new ArticleDO();
            article.setId(100L);
            article.setPublished(1);
            article.setTitle("Spring Security 实战");
            article.setContent("## JWT 过滤器\n\nSpring Security 中 JWT 认证主要通过 OncePerRequestFilter 实现。\n\n" +
                "### 配置步骤\n\n1. 创建 JwtAuthenticationFilter\n2. 注册到 SecurityFilterChain\n3. 配置 permitAll");

            when(docMapper.findBySourceRef(1L, "ARTICLE", "100")).thenReturn(null);

            service.syncArticle(article);

            // 验证 insert 被调，updateById 至少被调（CHUNKING + READY）
            verify(docMapper).insert(any(KnowledgeDocumentDO.class));
            verify(chunkMapper, atLeastOnce()).insert(any(KnowledgeChunkDO.class));
            verify(vectorStore, atLeastOnce()).insert(anyList());
            verify(docMapper, atLeast(2)).updateById(any(KnowledgeDocumentDO.class));
        }
    }

    @Nested
    @DisplayName("幂等：重复同步")
    class Idempotent {

        @Test
        @DisplayName("已有旧文档 → 删旧向量/分块/文档 → 建新")
        void shouldRemoveOldBeforeNew() {
            ArticleDO article = new ArticleDO();
            article.setId(300L);
            article.setPublished(1);
            article.setTitle("更新后的标题");
            article.setContent("## 更新后的内容\n\n这是更新后的文章正文。");

            KnowledgeDocumentDO existingDoc = new KnowledgeDocumentDO();
            existingDoc.setId(999L);
            existingDoc.setKbId(1L);
            when(docMapper.findBySourceRef(1L, "ARTICLE", "300")).thenReturn(existingDoc);

            service.syncArticle(article);

            verify(vectorStore).deleteByDocId(999L);
            verify(chunkMapper).deleteByDocId(999L);
            verify(docMapper).deleteById(999L);
            verify(docMapper).insert(any(KnowledgeDocumentDO.class));
        }
    }

    @Nested
    @DisplayName("失败场景")
    class FailureCases {

        @Test
        @DisplayName("embedding API 异常 → FAILED + fail_reason")
        void embeddingFailureShouldMarkFailed() {
            EmbeddingService failingEmb = mock(EmbeddingService.class);
            when(failingEmb.embedBatch(anyList()))
                .thenThrow(new RuntimeException("连接超时: dashscope.aliyuncs.com"));

            KnowledgeDocumentService failingSvc = new KnowledgeDocumentService(
                kbMapper, docMapper, chunkMapper, chunker, failingEmb, vectorStore);

            ArticleDO article = new ArticleDO();
            article.setId(400L);
            article.setPublished(1);
            article.setTitle("会失败的文章");
            article.setContent("## 测试\n\n这段内容会因为 embedding 失败而标记为 FAILED。");

            when(docMapper.findBySourceRef(1L, "ARTICLE", "400")).thenReturn(null);

            failingSvc.syncArticle(article);

            // updateById 被调两次：CHUNKING + FAILED
            verify(docMapper, atLeast(2)).updateById(any(KnowledgeDocumentDO.class));
        }

        @Test
        @DisplayName("已创建文档后 embedding 失败 → 记录仍更新为 FAILED")
        void failureAfterDocCreationShouldUpdateRecord() {
            EmbeddingService failingEmb = mock(EmbeddingService.class);
            when(failingEmb.embedBatch(anyList()))
                .thenThrow(new RuntimeException("API 限流"));

            KnowledgeDocumentService failingSvc = new KnowledgeDocumentService(
                kbMapper, docMapper, chunkMapper, chunker, failingEmb, vectorStore);

            ArticleDO article = new ArticleDO();
            article.setId(500L);
            article.setPublished(1);
            article.setTitle("失败2");
            article.setContent("内容");

            when(docMapper.findBySourceRef(1L, "ARTICLE", "500")).thenReturn(null);

            failingSvc.syncArticle(article);

            // insert 先调，updateById 两次（CHUNKING + FAILED）
            verify(docMapper).insert(any(KnowledgeDocumentDO.class));
            verify(docMapper, atLeast(2)).updateById(any(KnowledgeDocumentDO.class));
        }
    }

    @Nested
    @DisplayName("删除清理：removeArticle")
    class RemoveArticle {

        @Test
        @DisplayName("文章存在于多个知识库 → 逐个删除向量/分块/文档")
        void shouldRemoveDocsAcrossAllKbs() {
            KnowledgeDocumentDO doc1 = new KnowledgeDocumentDO();
            doc1.setId(9001L);
            doc1.setKbId(1L);
            KnowledgeDocumentDO doc2 = new KnowledgeDocumentDO();
            doc2.setId(9002L);
            doc2.setKbId(2L);
            when(docMapper.findBySource("ARTICLE", "700")).thenReturn(List.of(doc1, doc2));

            service.removeArticle(700L);

            verify(vectorStore).deleteByDocId(9001L);
            verify(vectorStore).deleteByDocId(9002L);
            verify(chunkMapper).deleteByDocId(9001L);
            verify(chunkMapper).deleteByDocId(9002L);
            verify(docMapper).deleteById(9001L);
            verify(docMapper).deleteById(9002L);
        }

        @Test
        @DisplayName("知识库中无该文章 → 静默跳过")
        void shouldDoNothingWhenNoDocs() {
            when(docMapper.findBySource("ARTICLE", "800")).thenReturn(List.of());

            service.removeArticle(800L);

            verify(vectorStore, never()).deleteByDocId(anyLong());
            verify(chunkMapper, never()).deleteByDocId(anyLong());
            verify(docMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("向量删除异常 → 被吞掉，不向外抛（清理失败不影响文章删除主流程）")
        void shouldSwallowException() {
            KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
            doc.setId(9003L);
            when(docMapper.findBySource("ARTICLE", "900")).thenReturn(List.of(doc));
            doThrow(new RuntimeException("PG 连接断开")).when(vectorStore).deleteByDocId(9003L);

            assertDoesNotThrow(() -> service.removeArticle(900L));
        }
    }

    @Test
    @DisplayName("没有启用的知识库 → 跳过同步（不再自动创建默认 KB）")
    void shouldSkipSyncWhenNoEnabledKb() {
        // 当前设计（对齐 Dify/FastGPT）：不设"默认 KB"，数据源显式绑定 KB；
        // 没有启用的知识库时直接跳过同步，而不是自动建库
        when(kbMapper.selectList(any())).thenReturn(List.of());

        ArticleDO article = new ArticleDO();
        article.setId(600L);
        article.setPublished(1);
        article.setTitle("第一篇");
        article.setContent("内容");

        service.syncArticle(article);

        verify(kbMapper, never()).insert(any(KnowledgeBaseDO.class));
        verify(docMapper, never()).insert(any(KnowledgeDocumentDO.class));
        verify(embeddingService, never()).embedBatch(anyList());
    }

    @Nested
    @DisplayName("公开性门：草稿/私有文章不入库")
    class PublicGate {

        @Test
        @DisplayName("草稿文章 → 不同步，且清掉知识库里已入库的旧索引")
        void draftArticleShouldSkipAndRemoveStale() {
            ArticleDO draft = new ArticleDO();
            draft.setId(700L);
            draft.setPublished(0);
            draft.setTitle("草稿");
            draft.setContent("未发布内容");

            KnowledgeDocumentDO stale = new KnowledgeDocumentDO();
            stale.setId(9001L);
            stale.setKbId(1L);
            when(docMapper.findBySource("ARTICLE", "700")).thenReturn(List.of(stale));

            service.syncArticle(draft);

            verify(docMapper, never()).insert(any(KnowledgeDocumentDO.class));
            verify(embeddingService, never()).embedBatch(anyList());
            verify(vectorStore).deleteByDocId(9001L);
            verify(docMapper).deleteById(9001L);
        }

        @Test
        @DisplayName("私有文章（visibility=1）→ 同样跳过")
        void privateArticleShouldSkip() {
            ArticleDO priv = new ArticleDO();
            priv.setId(702L);
            priv.setPublished(1);
            priv.setVisibility(1);
            priv.setTitle("私有");
            priv.setContent("内容");

            service.syncArticle(priv);

            verify(docMapper, never()).insert(any(KnowledgeDocumentDO.class));
            verify(embeddingService, never()).embedBatch(anyList());
        }

        @Test
        @DisplayName("显式指定知识库同步草稿 → 直接跳过，不触发入库")
        void draftArticleToSpecificKbShouldSkip() {
            ArticleDO draft = new ArticleDO();
            draft.setId(701L);
            draft.setPublished(0);
            draft.setTitle("草稿");
            draft.setContent("内容");

            service.syncArticle(draft, 1L);

            verify(docMapper, never()).insert(any(KnowledgeDocumentDO.class));
            verify(embeddingService, never()).embedBatch(anyList());
        }
    }

    @Nested
    @DisplayName("多知识库同步：分块/embedding 只做一次")
    class MultiKb {

        private KnowledgeBaseDO kb2;

        @BeforeEach
        void setUpKb2() {
            kb2 = new KnowledgeBaseDO();
            kb2.setId(2L);
            kb2.setName("第二知识库");
            kb2.setCollectionName("kb2");
            kb2.setEmbeddingModel("text-embedding-v4");
            kb2.setEmbeddingDimension(1024);
            when(kbMapper.selectList(any())).thenReturn(List.of(defaultKb, kb2));
        }

        private ArticleDO publicArticle(long id) {
            ArticleDO article = new ArticleDO();
            article.setId(id);
            article.setPublished(1);
            article.setTitle("多库同步文章");
            article.setContent("## 标题\n\n第一段内容。\n\n第二段内容。\n\n第三段内容。");
            return article;
        }

        /** 记录 updateById 调用时刻的 status（doc 对象事后会被原地改状态，事后断言不可靠） */
        private List<String> recordUpdateStatuses() {
            List<String> statuses = new ArrayList<>();
            doAnswer(inv -> {
                statuses.add(((KnowledgeDocumentDO) inv.getArgument(0)).getStatus());
                return 1;
            }).when(docMapper).updateById(any(KnowledgeDocumentDO.class));
            return statuses;
        }

        @Test
        @DisplayName("2 个 KB → embedBatch 只调 1 次，向量按 KB 各批量写一次，两 doc 均 READY")
        void shouldEmbedOnceAndWriteVectorsPerKb() {
            List<String> statuses = recordUpdateStatuses();

            service.syncArticle(publicArticle(800L));

            verify(embeddingService, times(1)).embedBatch(anyList());
            ArgumentCaptor<List<VectorStore.VectorEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore, times(2)).insert(captor.capture());
            assertEquals(2, captor.getAllValues().size());
            assertTrue(captor.getAllValues().stream().allMatch(l -> !l.isEmpty()));
            // 两个 KB 各自独立的 chunkId/kbId
            assertNotEquals(
                captor.getAllValues().get(0).get(0).kbId(),
                captor.getAllValues().get(1).get(0).kbId());

            assertEquals(List.of("CHUNKING", "CHUNKING", "READY", "READY"), statuses);
            verify(docMapper, times(2)).insert(any(KnowledgeDocumentDO.class));
        }

        @Test
        @DisplayName("embeddingText 去重：重复文本只 embed 一次，向量按原 chunk 序扩回")
        void shouldDeduplicateEmbeddingTexts() {
            DocumentChunker mockChunker = mock(DocumentChunker.class);
            when(mockChunker.chunk(anyString(), any(), any())).thenReturn(List.of(
                new DocumentChunker.Chunk(0L, 0L, 0, "展示0", "text-A"),
                new DocumentChunker.Chunk(0L, 0L, 1, "展示1", "text-B"),
                new DocumentChunker.Chunk(0L, 0L, 2, "展示2", "text-A")
            ));
            // 可区分向量：text-A → 全 1.0，text-B → 全 2.0
            when(embeddingService.embedBatch(anyList())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                List<String> texts = inv.getArgument(0);
                return texts.stream().map(t -> {
                    float[] v = new float[4];
                    Arrays.fill(v, "text-A".equals(t) ? 1f : 2f);
                    return v;
                }).toList();
            });
            // 本用例只关心去重扩回，收窄回单 KB
            when(kbMapper.selectList(any())).thenReturn(List.of(defaultKb));
            KnowledgeDocumentService svc = new KnowledgeDocumentService(
                kbMapper, docMapper, chunkMapper, mockChunker, embeddingService, vectorStore);

            svc.syncArticle(publicArticle(801L));

            ArgumentCaptor<List<String>> textsCaptor = ArgumentCaptor.forClass(List.class);
            verify(embeddingService, times(1)).embedBatch(textsCaptor.capture());
            assertEquals(List.of("text-A", "text-B"), textsCaptor.getValue());

            ArgumentCaptor<List<VectorStore.VectorEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore).insert(captor.capture());
            List<VectorStore.VectorEntry> entries = captor.getValue();
            assertEquals(3, entries.size());
            assertEquals(1f, entries.get(0).embedding()[0]);
            assertEquals(2f, entries.get(1).embedding()[0]);
            assertEquals(1f, entries.get(2).embedding()[0]);  // text-A 复用首个向量
        }

        @Test
        @DisplayName("embedding 失败 → 所有已建档 doc 标 FAILED，向量不写入")
        void embedFailureShouldFailAllDocs() {
            EmbeddingService failingEmb = mock(EmbeddingService.class);
            when(failingEmb.embedBatch(anyList()))
                .thenThrow(new RuntimeException("API 限流"));
            List<String> statuses = recordUpdateStatuses();
            KnowledgeDocumentService svc = new KnowledgeDocumentService(
                kbMapper, docMapper, chunkMapper, chunker, failingEmb, vectorStore);

            svc.syncArticle(publicArticle(802L));

            assertEquals(List.of("CHUNKING", "CHUNKING", "FAILED", "FAILED"), statuses);
            verify(vectorStore, never()).insert(anyList());
        }
    }
}
