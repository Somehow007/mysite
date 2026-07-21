package io.github.somehow.mysite.ragent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.mapper.*;
import io.github.somehow.mysite.ragent.ingestion.*;
import io.github.somehow.mysite.ragent.llm.*;
import io.github.somehow.mysite.ragent.service.KnowledgeDocumentService;
import io.github.somehow.mysite.ragent.vector.*;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 2 端到端集成测试。
 *
 * 覆盖 Phase 2 验收标准中阻塞的两项：
 *   1. 事件链验证：ArticleEventListener 收到事件后调用 KnowledgeDocumentService
 *   2. 完整链路：发布文章 → 分块 → Embedding → 向量入库 → search 检索
 *
 * 运行条件：
 *   - PostgreSQL + pgvector 必须运行（docker compose up -d postgres）
 *   - Part 2（完整链路）需要 BAILIAN_API_KEY 环境变量
 *
 * 使用方式：
 *   # 只跑事件链（不需要 API key）：
 *   docker compose -f docker/docker-compose.yml up -d postgres
 *   ./mvnw test -Dtest=Phase2EndToEndTest -pl .
 *
 *   # 跑完整链路（需要 API key）：
 *   export BAILIAN_API_KEY="sk-xxx"
 *   docker compose -f docker/docker-compose.yml up -d postgres
 *   ./mvnw test -Dtest=Phase2EndToEndTest -pl .
 */
@DisplayName("Phase 2 端到端测试")
class Phase2EndToEndTest {

    // ============ 共享的 PG 连接信息 ============
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ragent";
    private static final String PG_USER = "somehow";
    private static final String PG_PASSWORD = "ragent123";
    private static final String EMBEDDING_MODEL = "text-embedding-v4";
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    // ============ Part 1: 事件链验证 ============

    @Nested
    @DisplayName("Part 1 — 事件发布与监听")
    class EventChain {

        @Test
        @DisplayName("ArticleCreatedEvent → ArticleEventListener → KnowledgeDocumentService.syncArticle")
        void articleCreatedEventShouldTriggerSyncArticle() {
            // Given: Mock KnowledgeDocumentService
            KnowledgeDocumentService mockDocService = mock(KnowledgeDocumentService.class);
            ArticleEventListener listener = new ArticleEventListener(mockDocService);

            ArticleDO article = new ArticleDO();
            article.setId(100L);
            article.setTitle("Spring Security 实战");
            article.setContent("## JWT 过滤器\n\nJWT 认证主要通过 OncePerRequestFilter 实现...");

            // When: 发布事件
            listener.handleArticleCreated(new ArticleCreatedEvent(article));

            // Then: syncArticle 被调用
            verify(mockDocService).syncArticle(article);
        }

        @Test
        @DisplayName("ArticleUpdatedEvent → ArticleEventListener → KnowledgeDocumentService.syncArticle")
        void articleUpdatedEventShouldTriggerSyncArticle() {
            // Given
            KnowledgeDocumentService mockDocService = mock(KnowledgeDocumentService.class);
            ArticleEventListener listener = new ArticleEventListener(mockDocService);

            ArticleDO article = new ArticleDO();
            article.setId(200L);
            article.setTitle("更新后的文章");
            article.setContent("更新后的内容...");

            // When
            listener.handleArticleUpdated(new ArticleUpdatedEvent(article));

            // Then
            verify(mockDocService).syncArticle(article);
        }
    }

    // ============ Part 2: 完整链路（需要 BAILIAN_API_KEY） ============

    @Nested
    @DisplayName("Part 2 — 完整链路：文章 → 分块 → Embedding → 入库 → 检索")
    class FullPipeline {

        private static javax.sql.DataSource dataSource;
        private static EmbeddingService embeddingService;

        private PgvectorVectorStore vectorStore;
        private KnowledgeDocumentService docService;
        private KnowledgeBaseMapper kbMapper;
        private KnowledgeDocumentMapper docMapper;
        private KnowledgeChunkMapper chunkMapper;
        private MarkdownChunker chunker;
        private DocumentChunker chunkerInterface;

        private static final long TEST_KB_ID = 99999L;

        @BeforeAll
        static void checkPrerequisites() {
            // 1. 检查 PG 连接
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(JDBC_URL, PG_USER, PG_PASSWORD);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_extension WHERE extname='vector'")) {
                    assumeTrue(rs.next(), "跳过：pgvector 扩展未安装");
                }
            } catch (Exception e) {
                assumeTrue(false, "跳过：无法连接 PostgreSQL — " + e.getMessage());
            }

            // 2. 检查百炼 API Key
            String apiKey = loadApiKey();
            assumeTrue(apiKey != null && !apiKey.isBlank(),
                "跳过：未设置 BAILIAN_API_KEY 环境变量。\n" +
                "  设置方式：export BAILIAN_API_KEY=\"sk-xxx\"\n" +
                "  获取地址：https://bailian.console.aliyun.com/");

            // 3. 创建 DataSource
            dataSource = new DataSource();

            // 4. 创建 EmbeddingService（使用 package-private 测试构造器）
            //    API key 在此处硬编码时直接从环境变量读取
            embeddingService = new BaiLianEmbeddingService(
                WebClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build(),
                EMBEDDING_MODEL,
                new ObjectMapper(),
                10  // maxBatchSize
            );
        }

        @BeforeEach
        void setUp() throws Exception {
            vectorStore = new PgvectorVectorStore(dataSource);

            // 清理旧数据
            cleanupTestData();

            // 确保 PG 里有 t_knowledge_base 记录（满足外键约束）
            ensureTestKnowledgeBase();

            // 模拟 MyBatis-Plus Mapper：用 JDBC 直接操作
            kbMapper = mock(KnowledgeBaseMapper.class);
            docMapper = mock(KnowledgeDocumentMapper.class);
            chunkMapper = mock(KnowledgeChunkMapper.class);

            // getOrCreateDefaultKb 需要 kbMapper.selectList 返回默认知识库
            KnowledgeBaseDO defaultKb = new KnowledgeBaseDO();
            defaultKb.setId(TEST_KB_ID);
            defaultKb.setCollectionName("test-collection");
            defaultKb.setEmbeddingModel(EMBEDDING_MODEL);
            defaultKb.setEmbeddingDimension(1024);
            defaultKb.setChunkSize(800);
            defaultKb.setChunkOverlap(100);

            when(kbMapper.selectList(any())).thenReturn(List.of(defaultKb));

            // insert 时自动给 ID（模拟雪花算法）
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

            // chunkMapper.deleteByDocId
            doAnswer(inv -> {
                long docId = inv.getArgument(0);
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM t_knowledge_chunk WHERE doc_id = ?")) {
                    ps.setLong(1, docId);
                    ps.executeUpdate();
                }
                return 1;
            }).when(chunkMapper).deleteByDocId(anyLong());

            // docMapper.deleteById
            doAnswer(inv -> {
                long id = inv.getArgument(0);
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM t_knowledge_document WHERE id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                return 1;
            }).when(docMapper).deleteById(anyLong());

            // docMapper.findBySourceRef
            doAnswer(inv -> {
                long kbId = inv.getArgument(0);
                String sourceType = inv.getArgument(1);
                String sourceRef = inv.getArgument(2);
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM t_knowledge_document WHERE kb_id = ? AND source_type = ? AND source_ref = ?")) {
                    ps.setLong(1, kbId);
                    ps.setString(2, sourceType);
                    ps.setString(3, sourceRef);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
                        doc.setId(rs.getLong("id"));
                        doc.setKbId(rs.getLong("kb_id"));
                        doc.setSourceType(rs.getString("source_type"));
                        doc.setSourceRef(rs.getString("source_ref"));
                        return doc;
                    }
                    return null;
                }
            }).when(docMapper).findBySourceRef(anyLong(), anyString(), anyString());

            // docMapper.insert 真正写入 PG（验证数据持久化）
            doAnswer(inv -> {
                KnowledgeDocumentDO doc = inv.getArgument(0);
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO t_knowledge_document (id, kb_id, title, source_type, source_ref, file_type, status) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status")) {
                    if (doc.getId() == null) doc.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                    ps.setLong(1, doc.getId());
                    ps.setLong(2, doc.getKbId());
                    ps.setString(3, doc.getTitle());
                    ps.setString(4, doc.getSourceType());
                    ps.setString(5, doc.getSourceRef());
                    ps.setString(6, doc.getFileType());
                    ps.setString(7, doc.getStatus());
                    ps.executeUpdate();
                    return 1;
                }
            }).when(docMapper).insert(any(KnowledgeDocumentDO.class));

            // chunkMapper.insert 真正写入 PG
            doAnswer(inv -> {
                KnowledgeChunkDO chunk = inv.getArgument(0);
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO t_knowledge_chunk (id, doc_id, kb_id, chunk_index, content) " +
                         "VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content")) {
                    if (chunk.getId() == null) chunk.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                    ps.setLong(1, chunk.getId());
                    ps.setLong(2, chunk.getDocId());
                    ps.setLong(3, chunk.getKbId());
                    ps.setInt(4, chunk.getChunkIndex());
                    ps.setString(5, chunk.getContent());
                    ps.executeUpdate();
                    return 1;
                }
            }).when(chunkMapper).insert(any(KnowledgeChunkDO.class));

            // docMapper.updateById 真正更新 PG
            doAnswer(inv -> {
                KnowledgeDocumentDO doc = inv.getArgument(0);
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "UPDATE t_knowledge_document SET status = ?, chunk_count = ?, char_count = ?, fail_reason = ? WHERE id = ?")) {
                    ps.setString(1, doc.getStatus());
                    if (doc.getChunkCount() != null) ps.setInt(2, doc.getChunkCount());
                    else ps.setNull(2, Types.INTEGER);
                    if (doc.getCharCount() != null) ps.setInt(3, doc.getCharCount());
                    else ps.setNull(3, Types.INTEGER);
                    ps.setString(4, doc.getFailReason());
                    ps.setLong(5, doc.getId());
                    ps.executeUpdate();
                    return 1;
                }
            }).when(docMapper).updateById(any(KnowledgeDocumentDO.class));

            // 创建 chunker
            RagProperties props = new RagProperties();
            props.getChunk().setSize(800);
            props.getChunk().setOverlap(100);
            props.getChunk().setMaxChunksPerDoc(50);
            chunker = new MarkdownChunker(props);
            chunkerInterface = chunker;

            // 组装 KnowledgeDocumentService
            docService = new KnowledgeDocumentService(
                kbMapper, docMapper, chunkMapper, chunkerInterface, embeddingService, vectorStore);
        }

        @AfterEach
        void tearDown() {
            cleanupTestData();
        }

        @Test
        @DisplayName("完整链路：文章 → 分块 → Embedding → 入库 → search 检索")
        void fullPipelineShouldIngestAndSearch() {
            // Step 1: 创建测试文章
            ArticleDO article = new ArticleDO();
            article.setId(10001L);
            article.setTitle("Spring Security JWT 认证配置指南");
            article.setContent("""
                ## JWT 过滤器配置

                Spring Security 中的 JWT 认证主要通过 OncePerRequestFilter 实现。
                它可以确保每个请求只被过滤一次，避免在转发和包含时重复执行。

                ### 核心配置步骤

                1. 创建 JwtAuthenticationFilter 继承 OncePerRequestFilter
                2. 在 SecurityFilterChain 中注册过滤器
                3. 配置 permitAll 和 authenticated 路径

                ### 代码示例

                ```java
                @Component
                public class JwtAuthenticationFilter extends OncePerRequestFilter {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain filterChain) {
                        String token = extractToken(request);
                        if (token != null && jwtService.validateToken(token)) {
                            Authentication auth = jwtService.getAuthentication(token);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                        filterChain.doFilter(request, response);
                    }
                }
                ```
                """);

            // Step 2: 执行同步（异步 @Async 在测试中直接同步调用）
            docService.syncArticle(article);

            // Step 3: 验证 PG 中有数据
            try (Connection conn = dataSource.getConnection()) {
                // 3a. 文档记录已创建且状态为 READY
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, status, chunk_count, fail_reason FROM t_knowledge_document " +
                    "WHERE source_type = 'ARTICLE' AND source_ref = ? AND kb_id = ?")) {
                    ps.setString(1, "10001");
                    ps.setLong(2, TEST_KB_ID);
                    ResultSet rs = ps.executeQuery();
                    assertTrue(rs.next(), "应有文档记录");
                    String status = rs.getString("status");
                    String failReason = rs.getString("fail_reason");
                    assertEquals("READY", status,
                        "文档状态应为 READY，实际: " + status
                        + (failReason != null ? "，fail_reason: " + failReason : ""));
                    assertTrue(rs.getInt("chunk_count") > 0, "应有至少 1 个 chunk");
                    System.out.println("✅ 文档已入库: status=READY, chunks=" + rs.getInt("chunk_count"));
                }

                // 3b. chunk 记录已创建
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM t_knowledge_chunk WHERE kb_id = ?")) {
                    ps.setLong(1, TEST_KB_ID);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    int chunkCount = rs.getInt(1);
                    assertTrue(chunkCount > 0, "应有 chunk 记录");
                    System.out.println("✅ Chunks: " + chunkCount + " 条");
                }

                // 3c. 向量记录已创建
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM t_knowledge_vector WHERE kb_id = ?")) {
                    ps.setLong(1, TEST_KB_ID);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    int vectorCount = rs.getInt(1);
                    assertTrue(vectorCount > 0, "应有向量记录");
                    System.out.println("✅ Vectors: " + vectorCount + " 条");
                }
            } catch (SQLException e) {
                fail("DB 查询失败: " + e.getMessage());
            }

            // Step 4: 向量检索 —— 用相关问题搜索能找到文章片段
            float[] queryEmbedding = embeddingService.embed("JWT 过滤器怎么配置的？");
            List<VectorStore.SearchResult> results = vectorStore.search(queryEmbedding, 3, null);

            assertFalse(results.isEmpty(), "应能检索到相关片段");
            // 至少有一条结果的相似度 > 0.3（内容相关）
            assertTrue(results.stream().anyMatch(r -> r.score() > 0.3f),
                "至少有一条高相关性结果（score > 0.3）");
            System.out.println("✅ 检索完成: " + results.size() + " 条结果");
            for (int i = 0; i < results.size(); i++) {
                System.out.println("  [" + i + "] score=" + String.format("%.4f", results.get(i).score())
                    + ", doc=" + results.get(i).docTitle()
                    + ", content=" + results.get(i).content().substring(0,
                        Math.min(80, results.get(i).content().length())) + "...");
            }
        }

        @Test
        @DisplayName("失败路径：错误 API Key → 文档标记 FAILED")
        void badApiKeyShouldMarkDocumentFailed() {
            // 用错误的 API key 创建 embedding service
            EmbeddingService badEmbedding = new BaiLianEmbeddingService(
                WebClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("Authorization", "Bearer sk-invalid-key-for-test")
                    .defaultHeader("Content-Type", "application/json")
                    .build(),
                EMBEDDING_MODEL,
                new ObjectMapper(),
                10
            );

            KnowledgeDocumentService failingDocService = new KnowledgeDocumentService(
                kbMapper, docMapper, chunkMapper, chunkerInterface, badEmbedding, vectorStore);

            ArticleDO article = new ArticleDO();
            article.setId(20001L);
            article.setTitle("失败测试");
            article.setContent("这段内容的 embedding 会失败。");

            // 执行同步（预期不抛异常，而是标记 FAILED）
            assertDoesNotThrow(() -> failingDocService.syncArticle(article),
                "syncArticle 不应向外抛异常，应内部捕获并标记 FAILED");

            // 验证 docMapper.updateById 被调用时 status 为 FAILED
            // 显式指定泛型避免 BaseMapper 的两个 updateById 重载歧义
            verify(docMapper, atLeast(1)).updateById(
                org.mockito.Mockito.<KnowledgeDocumentDO>argThat(doc ->
                    "FAILED".equals(doc.getStatus()) && doc.getFailReason() != null
                ));
            System.out.println("✅ 失败路径验证成功：文档已标记 FAILED");
        }

        @Test
        @DisplayName("幂等性：重复同步不产生重复文档")
        void duplicateSyncShouldBeIdempotent() {
            ArticleDO article = new ArticleDO();
            article.setId(30001L);
            article.setTitle("幂等测试");
            article.setContent("## 幂等性\n\n这段内容会被同步两次，不应产生重复数据。");

            // 第一次同步
            docService.syncArticle(article);

            // 第二次同步
            docService.syncArticle(article);

            // 验证：数据库中该 source_ref 只有一条文档记录
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM t_knowledge_document " +
                     "WHERE source_type = 'ARTICLE' AND source_ref = ? AND kb_id = ?")) {
                ps.setString(1, "30001");
                ps.setLong(2, TEST_KB_ID);
                ResultSet rs = ps.executeQuery();
                rs.next();
                assertEquals(1, rs.getInt(1),
                    "同一 source_ref 只应有一条文档记录（幂等）");
                System.out.println("✅ 幂等性验证通过：重复同步不产生重复文档");
            } catch (SQLException e) {
                fail("DB 查询失败: " + e.getMessage());
            }
        }

        // ============ 工具方法 ============

        /** 加载 API Key：先读环境变量，再读 .env 文件 */
        private static String loadApiKey() {
            String key = System.getenv("BAILIAN_API_KEY");
            if (key != null && !key.isBlank()) return key;

            // 尝试从项目根目录 .env 读取（支持 export KEY=val 和 KEY=val 两种格式）
            try {
                java.nio.file.Path envFile = java.nio.file.Path.of(".env");
                if (java.nio.file.Files.exists(envFile)) {
                    for (String line : java.nio.file.Files.readAllLines(envFile)) {
                        String trimmed = line.strip();
                        // 去掉可选的 "export " 前缀
                        if (trimmed.startsWith("export ")) {
                            trimmed = trimmed.substring(7);
                        }
                        // 匹配 KEY=VALUE
                        if (trimmed.startsWith("BAILIAN_API_KEY=")) {
                            key = trimmed.substring("BAILIAN_API_KEY=".length()).trim();
                            // 去掉可选的双引号
                            if (key.startsWith("\"") && key.endsWith("\"")) {
                                key = key.substring(1, key.length() - 1);
                            }
                            if (!key.isBlank()) return key;
                        }
                    }
                }
                System.out.println("apikey: " + key);
            } catch (Exception ignored) { }

            return null;
        }

        private void ensureTestKnowledgeBase() {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO t_knowledge_base (id, name, collection_name) " +
                     "VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING")) {
                ps.setLong(1, TEST_KB_ID);
                ps.setString(2, "test-kb");
                ps.setString(3, "test-collection");
                ps.executeUpdate();
            } catch (SQLException e) {
                fail("无法创建测试知识库: " + e.getMessage());
            }
        }

        private void cleanupTestData() {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute(
                    "DELETE FROM t_knowledge_vector WHERE kb_id = " + TEST_KB_ID);
                conn.createStatement().execute(
                    "DELETE FROM t_knowledge_chunk WHERE kb_id = " + TEST_KB_ID);
                conn.createStatement().execute(
                    "DELETE FROM t_knowledge_document WHERE kb_id = " + TEST_KB_ID);
            } catch (SQLException e) {
                // 忽略清理失败
            }
        }

        /** 简单的 DataSource 实现（与 PgvectorVectorStoreTest 模式一致） */
        private static class DataSource implements javax.sql.DataSource {
            @Override public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(JDBC_URL, PG_USER, PG_PASSWORD);
            }
            @Override public Connection getConnection(String username, String password) throws SQLException {
                return DriverManager.getConnection(JDBC_URL, username, password);
            }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getLogger("test");
            }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        }
    }
}
