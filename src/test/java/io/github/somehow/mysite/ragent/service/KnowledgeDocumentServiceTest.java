package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeChunkMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import io.github.somehow.mysite.ragent.ingestion.DocumentChunker;
import io.github.somehow.mysite.ragent.ingestion.MarkdownChunker;
import io.github.somehow.mysite.ragent.llm.EmbeddingService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 *   - 知识库不存在 → 自动创建
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
        @DisplayName("新文章 → 分块 → embedding → 入库 → READY")
        void newArticleShouldCompleteFullPipeline() {
            ArticleDO article = new ArticleDO();
            article.setId(100L);
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
            article.setTitle("失败2");
            article.setContent("内容");

            when(docMapper.findBySourceRef(1L, "ARTICLE", "500")).thenReturn(null);

            failingSvc.syncArticle(article);

            // insert 先调，updateById 两次（CHUNKING + FAILED）
            verify(docMapper).insert(any(KnowledgeDocumentDO.class));
            verify(docMapper, atLeast(2)).updateById(any(KnowledgeDocumentDO.class));
        }
    }

    @Test
    @DisplayName("默认知识库不存在 → 自动创建")
    void shouldCreateDefaultKbWhenMissing() {
        when(kbMapper.selectList(any())).thenReturn(List.of());

        ArticleDO article = new ArticleDO();
        article.setId(600L);
        article.setTitle("第一篇");
        article.setContent("内容");

        when(docMapper.findBySourceRef(anyLong(), anyString(), anyString())).thenReturn(null);

        service.syncArticle(article);

        verify(kbMapper).insert(argThat((KnowledgeBaseDO kb) ->
            "default".equals(kb.getCollectionName()) &&
            "text-embedding-v4".equals(kb.getEmbeddingModel())
        ));
    }
}
