package io.github.somehow.mysite.ragent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.core.PromptTemplate;
import io.github.somehow.mysite.ragent.core.memory.ConversationManager;
import io.github.somehow.mysite.ragent.core.retrieval.RetrievalEngine;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeChunkMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import io.github.somehow.mysite.ragent.ingestion.MarkdownChunker;
import io.github.somehow.mysite.ragent.llm.*;
import io.github.somehow.mysite.ragent.service.ChatRateLimiter;
import io.github.somehow.mysite.ragent.service.KnowledgeDocumentService;
import io.github.somehow.mysite.ragent.service.RagChatService;
import io.github.somehow.mysite.ragent.vector.PgvectorVectorStore;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.sql.*;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 3 集成测试 —— RAG 问答核心链路验证。
 *
 * <h3>解决了什么问题</h3>
 * <p>Phase 3 的 curl 验证依赖 PG 中有向量化后的测试数据。本地 PG 默认是空的，
 * 直接 curl 只会走通用聊天兜底（sources 为空），无法验证检索→RAG 完整链路。
 * 本测试<b>自动灌入测试文章 → 分块 → 向量化 → 再验证 RAG 聊天全链路</b>，
 * 一步到位。</p>
 *
 * <h3>架构</h3>
 * <p>采用与 {@link Phase2EndToEndTest} 相同的 JDBC 手动接线模式，不依赖 Spring Context，
 * 避免拉满 MySQL + Redis 等重量级依赖。核心验证点：</p>
 * <ul>
 *   <li>数据灌入成功：文档 → chunk → vector 完整入库</li>
 *   <li>检索质量：相关问题能命中正确的文章，score > 0.3</li>
 *   <li>事件序列：meta(含 conversationId) → sources(含检索来源) → content×N → done</li>
 *   <li>兜底模式：无匹配结果时正确走通用聊天</li>
 *   <li>错误降级：LLM 失败时正确返回 error 事件不裸断开</li>
 * </ul>
 *
 * <h3>运行条件</h3>
 * <ul>
 *   <li>PostgreSQL + pgvector 已启动: {@code docker compose -f docker/docker-compose.yml up -d postgres}</li>
 *   <li>BAILIAN_API_KEY 环境变量已设置: {@code export BAILIAN_API_KEY="sk-xxx"}</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 *   docker compose -f docker/docker-compose.yml up -d postgres
 *   export BAILIAN_API_KEY="sk-xxx"
 *   ./mvnw test -Dtest=Phase3IntegrationTest -pl .
 * }</pre>
 *
 * @see Phase2EndToEndTest Phase 2 端到端测试（数据灌入参考）
 */
@DisplayName("Phase 3 集成测试 — RAG 问答核心链路")
class Phase3IntegrationTest {

    // ============ PG 连接信息 ============
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ragent";
    private static final String PG_USER = "somehow";
    private static final String PG_PASSWORD = "ragent123";
    private static final String EMBEDDING_MODEL = "text-embedding-v4";
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private static final long TEST_KB_ID = 99998L;

    // ============ 共享资源（@BeforeAll 初始化一次） ============
    private static DataSource dataSource;
    private static EmbeddingService embeddingService;

    // ============ 每测试实例资源 ============
    private PgvectorVectorStore vectorStore;
    private RetrievalEngine retrievalEngine;
    private TestLLMProvider testLLMProvider;
    private RoutingLLMService routingLLMService;
    private RagChatService ragChatService;
    private ConversationManager conversationManager;
    private ChatRateLimiter rateLimiter;
    private KnowledgeDocumentService docService;

    // ============ 前置条件检查 ============

    @BeforeAll
    static void checkPrerequisites() {
        // 1. 检查 PG 连接
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(JDBC_URL, PG_USER, PG_PASSWORD);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM pg_extension WHERE extname='vector'")) {
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

        // 3. 创建共享 DataSource 和 EmbeddingService
        dataSource = new DataSource();
        embeddingService = new BaiLianEmbeddingService(
            WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build(),
            EMBEDDING_MODEL,
            new ObjectMapper(),
            10
        );
    }

    @BeforeEach
    void setUp() throws Exception {
        // 清理残留测试数据
        cleanupTestData();

        // 确保 PG 里有 t_knowledge_base 记录
        ensureTestKnowledgeBase();

        // 初始化 PG 向量存储
        vectorStore = new PgvectorVectorStore(dataSource);

        // ---- 构建 Mock Mapper（与 Phase2EndToEndTest 模式一致） ----
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper docMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);

        // kbMapper.selectList 返回默认知识库
        KnowledgeBaseDO defaultKb = new KnowledgeBaseDO();
        defaultKb.setId(TEST_KB_ID);
        defaultKb.setCollectionName("test-collection");
        defaultKb.setEmbeddingModel(EMBEDDING_MODEL);
        defaultKb.setEmbeddingDimension(1024);
        defaultKb.setChunkSize(800);
        defaultKb.setChunkOverlap(100);
        when(kbMapper.selectList(any())).thenReturn(List.of(defaultKb));

        // insert 自动给 ID
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

        // 真正的 DB 写入操作
        stubDocMapperDbOps(docMapper);
        stubChunkMapperDbOps(chunkMapper);

        // 创建 MarkdownChunker
        RagProperties chunkProps = new RagProperties();
        chunkProps.getChunk().setSize(800);
        chunkProps.getChunk().setOverlap(100);
        chunkProps.getChunk().setMaxChunksPerDoc(50);
        MarkdownChunker chunker = new MarkdownChunker(chunkProps);

        // 组装 KnowledgeDocumentService
        docService = new KnowledgeDocumentService(
            kbMapper, docMapper, chunkMapper, chunker, embeddingService, vectorStore);

        // ---- 构建 RAG Chat 管线组件 ----
        // Rerank: 暂不测试（需要额外配置），用 null RerankService → 走向量截断
        RagProperties ragProps = new RagProperties();
        ragProps.getRetrieval().setTopK(10);
        ragProps.getRetrieval().setRerankTopK(5);
        ragProps.getRetrieval().setScoreThreshold(0.3f);
        ragProps.getMemory().setKeepTurns(6);

        retrievalEngine = new RetrievalEngine(
            vectorStore, embeddingService, null, ragProps);

        // 测试用 LLM 供应商
        testLLMProvider = new TestLLMProvider();

        // 配置路由：只需要包含 test 供应商
        RagProperties routingRagProps = new RagProperties();
        routingRagProps.getLlm().getProviders().put("test", createTestProviderConfig());
        routingLLMService = new RoutingLLMService(List.of(testLLMProvider), routingRagProps);

        // Mock ConversationManager 和 ChatRateLimiter
        conversationManager = mock(ConversationManager.class);
        rateLimiter = mock(ChatRateLimiter.class);

        // 默认：新建会话
        ConversationDO conv = new ConversationDO();
        conv.setId(1001L);
        when(conversationManager.getOrCreateConversation(any(), anyString(), anyString()))
            .thenReturn(conv);
        when(conversationManager.loadHistory(anyLong())).thenReturn(List.of());

        // 构建 RagChatService
        ragChatService = new RagChatService(
            retrievalEngine, conversationManager,
            new PromptTemplate(), routingLLMService,
            rateLimiter, ragProps);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ============ Part 1: 数据灌入 ============

    @Nested
    @DisplayName("Part 1 — 数据灌入验证")
    class DataSeeding {

        @Test
        @DisplayName("灌入测试文章 → 分块 → 向量化 → 可检索")
        void seedAndRetrieve() throws Exception {
            // Step 1: 灌入文章
            ArticleDO article = new ArticleDO();
            article.setId(90001L);
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

                ### 注意事项

                过滤器必须注册在 UsernamePasswordAuthenticationFilter 之前，
                否则表单登录会先拦截请求。推荐使用 addFilterBefore 方法。
                """);

            docService.syncArticle(article);

            // Step 2: 验证 PG 中有数据
            try (Connection conn = dataSource.getConnection()) {
                // 2a. 文档记录
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, status, chunk_count FROM t_knowledge_document " +
                    "WHERE source_type = 'ARTICLE' AND source_ref = ? AND kb_id = ?")) {
                    ps.setString(1, "90001");
                    ps.setLong(2, TEST_KB_ID);
                    ResultSet rs = ps.executeQuery();
                    assertTrue(rs.next(), "应有文档记录");
                    String status = rs.getString("status");
                    assertEquals("READY", status,
                        "文档状态应为 READY，实际: " + status);
                    assertTrue(rs.getInt("chunk_count") > 0, "应有至少 1 个 chunk");
                    System.out.println("  ✅ 文档已入库: status=READY, chunks="
                        + rs.getInt("chunk_count"));
                }

                // 2b. chunk 记录
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM t_knowledge_chunk WHERE kb_id = ?")) {
                    ps.setLong(1, TEST_KB_ID);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    assertTrue(rs.getInt(1) > 0, "应有 chunk 记录");
                    System.out.println("  ✅ Chunks: " + rs.getInt(1) + " 条");
                }

                // 2c. 向量记录
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM t_knowledge_vector WHERE kb_id = ?")) {
                    ps.setLong(1, TEST_KB_ID);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    assertTrue(rs.getInt(1) > 0, "应有向量记录");
                    System.out.println("  ✅ Vectors: " + rs.getInt(1) + " 条");
                }
            }

            // Step 3: 检索验证 —— 相关问题应能找到文章
            float[] queryEmbedding = embeddingService.embed("JWT 过滤器怎么配置的？");
            List<VectorStore.SearchResult> results = vectorStore.search(queryEmbedding, 3, null);

            assertFalse(results.isEmpty(), "应能检索到相关片段");
            assertTrue(results.stream().anyMatch(r -> r.score() > 0.3f),
                "至少有一条高相关性结果（score > 0.3）");
            assertTrue(results.stream().anyMatch(r ->
                r.docTitle().contains("JWT") || r.docTitle().contains("Spring Security")),
                "检索结果标题应包含灌入的文章");

            System.out.println("  ✅ 检索完成: " + results.size() + " 条结果");
            for (int i = 0; i < results.size(); i++) {
                String snippet = results.get(i).content().length() > 80
                    ? results.get(i).content().substring(0, 80) + "..."
                    : results.get(i).content();
                System.out.println("    [" + i + "] score="
                    + String.format("%.4f", results.get(i).score())
                    + ", doc=" + results.get(i).docTitle()
                    + ", content=" + snippet);
            }
        }

        @Test
        @DisplayName("灌入多篇文章 — 检索结果区分不同主题")
        void multipleArticlesShouldBeDistinguishable() {
            // 灌入主题 A: JWT
            ArticleDO jwtArticle = new ArticleDO();
            jwtArticle.setId(90002L);
            jwtArticle.setTitle("Spring Security JWT 认证配置指南");
            jwtArticle.setContent("""
                ## JWT 过滤器

                JWT 认证主要通过 OncePerRequestFilter 实现。
                配置时需要继承该类并重写 doFilterInternal 方法。
                过滤器链的注册顺序非常重要。
                """);
            docService.syncArticle(jwtArticle);

            // 灌入主题 B: Redis
            ArticleDO redisArticle = new ArticleDO();
            redisArticle.setId(90003L);
            redisArticle.setTitle("Redis 缓存最佳实践");
            redisArticle.setContent("""
                ## 缓存策略

                Redis 常用于缓存热点数据，减少数据库压力。
                推荐使用旁路缓存模式（Cache Aside）：
                先读缓存，未命中再查数据库，并写回缓存。
                """);
            docService.syncArticle(redisArticle);

            // 检索 JWT 相关问题 → JWT 文章排前面
            float[] jwtEmbedding = embeddingService.embed("JWT 过滤器怎么配置？");
            List<VectorStore.SearchResult> jwtResults = vectorStore.search(jwtEmbedding, 5, null);

            assertFalse(jwtResults.isEmpty());
            // Top 1 应该与 JWT 相关
            assertTrue(
                jwtResults.get(0).docTitle().contains("JWT") ||
                jwtResults.get(0).content().contains("JWT"),
                "JWT 查询的 top-1 应与 JWT 相关");

            System.out.println("  ✅ JWT 检索: top-1 score="
                + String.format("%.4f", jwtResults.get(0).score())
                + ", title=" + jwtResults.get(0).docTitle());

            // 检索 Redis 相关问题 → Redis 文章排前面
            float[] redisEmbedding = embeddingService.embed("Redis 缓存怎么用？");
            List<VectorStore.SearchResult> redisResults = vectorStore.search(redisEmbedding, 5, null);

            assertFalse(redisResults.isEmpty());
            assertTrue(
                redisResults.get(0).docTitle().contains("Redis") ||
                redisResults.get(0).content().contains("Redis"),
                "Redis 查询的 top-1 应与 Redis 相关");

            System.out.println("  ✅ Redis 检索: top-1 score="
                + String.format("%.4f", redisResults.get(0).score())
                + ", title=" + redisResults.get(0).docTitle());
        }
    }

    // ============ Part 2: RAG 聊天全链路 ============

    @Nested
    @DisplayName("Part 2 — RAG 聊天全链路")
    class RagChatPipeline {

        /**
         * 灌入测试文章（Part 2 每个测试方法执行前调用）。
         */
        void seedTestArticle() {
            ArticleDO article = new ArticleDO();
            article.setId(91001L);
            article.setTitle("Spring Security JWT 认证配置指南");
            article.setContent("""
                ## JWT 过滤器配置

                Spring Security 中的 JWT 认证主要通过 OncePerRequestFilter 实现。
                它可以确保每个请求只被过滤一次，避免在转发和包含时重复执行。

                ### 核心配置步骤

                1. 创建 JwtAuthenticationFilter 继承 OncePerRequestFilter
                2. 在 SecurityFilterChain 中注册过滤器
                3. 配置 permitAll 和 authenticated 路径

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
            docService.syncArticle(article);
        }

        @Test
        @DisplayName("事件序列完整性：meta → sources → content → done")
        void eventSequenceShouldBeCorrect() {
            seedTestArticle();

            Flux<ChatEvent> stream = ragChatService.chat(
                "JWT 过滤器怎么配置？", null, "test-visitor", "127.0.0.1");

            List<ChatEvent> events = stream.collectList().block(Duration.ofSeconds(120));
            assertNotNull(events);
            assertTrue(events.size() >= 3,
                "至少应有 meta + sources + done 三个事件");

            // 序列验证
            assertEquals("meta", events.get(0).type(),
                "第一个事件应为 meta");
            assertNotNull(events.get(0).conversationId(),
                "meta 应包含 conversationId");

            assertEquals("sources", events.get(1).type(),
                "第二个事件应为 sources");

            // 应有 content 事件（测试 LLM 返回非空）
            boolean hasContent = events.stream().anyMatch(e -> "content".equals(e.type()));
            assertTrue(hasContent, "应至少有一个 content 事件");

            // content 事件应包含测试 LLM 的输出
            String fullResponse = events.stream()
                .filter(e -> "content".equals(e.type()))
                .map(ChatEvent::delta)
                .reduce("", String::concat);
            assertTrue(fullResponse.contains("OncePerRequestFilter"),
                "回答应包含检索到的文章内容（验证 RAG 上下文生效）");

            assertEquals("done", events.get(events.size() - 1).type(),
                "最后一个事件应为 done");

            System.out.println("  ✅ 事件序列正确: " + events.size() + " 个事件");
            System.out.println("    完整回答: " + fullResponse);
        }

        @Test
        @DisplayName("检索来源应在 sources 事件中携带")
        void sourcesEventShouldContainRetrievalResults() {
            seedTestArticle();

            Flux<ChatEvent> stream = ragChatService.chat(
                "JWT 过滤器怎么配置？", null, "test-visitor", "127.0.0.1");

            List<ChatEvent> events = stream.collectList().block(Duration.ofSeconds(120));
            assertNotNull(events);

            ChatEvent sourcesEvent = events.get(1);
            assertEquals("sources", sourcesEvent.type());
            assertNotNull(sourcesEvent.sources());
            assertFalse(sourcesEvent.sources().isEmpty(),
                "有检索数据时 sources 不应为空");

            // 验证来源内容
            assertTrue(sourcesEvent.sources().stream()
                .anyMatch(s -> s.getTitle().contains("JWT")
                    && s.getScore() > 0.3f),
                "来源应包含 JWT 文章且 score > 0.3");

            System.out.println("  ✅ Sources: " + sourcesEvent.sources().size() + " 条");
            sourcesEvent.sources().forEach(s ->
                System.out.println("    - " + s.getTitle()
                    + " (score=" + String.format("%.3f", s.getScore()) + ")"));
        }

        @Test
        @DisplayName("无匹配结果时走通用聊天兜底")
        void noResultsShouldFallbackToGeneralChat() {
            // 不灌数据 → 检索必然为空
            Flux<ChatEvent> stream = ragChatService.chat(
                "今天天气怎么样？", null, "test-visitor", "127.0.0.1");

            List<ChatEvent> events = stream.collectList().block(Duration.ofSeconds(120));
            assertNotNull(events);

            // sources 事件存在但数组为空
            ChatEvent sourcesEvent = events.stream()
                .filter(e -> "sources".equals(e.type()))
                .findFirst().orElse(null);
            assertNotNull(sourcesEvent, "sources 事件必须发送（即使为空）");
            assertTrue(sourcesEvent.sources().isEmpty(),
                "无检索结果时 sources 应为空数组");

            // 应走通用聊天模式（测试 LLM 返回非 RAG 回复）
            String fullResponse = events.stream()
                .filter(e -> "content".equals(e.type()))
                .map(ChatEvent::delta)
                .reduce("", String::concat);
            assertFalse(fullResponse.isEmpty(), "仍应有回复");

            System.out.println("  ✅ 兜底对话: " + fullResponse);
            System.out.println("     sources: 空数组（符合预期）");
        }

        @Test
        @DisplayName("LLM 失败时返回 error 事件（不裸断开）")
        void llmFailureShouldReturnErrorEvent() {
            // 让测试 LLM 抛出异常
            testLLMProvider.setShouldFail(true);

            Flux<ChatEvent> stream = ragChatService.chat(
                "测试问题", null, "test-visitor", "127.0.0.1");

            List<ChatEvent> events = stream.collectList().block(Duration.ofSeconds(120));
            assertNotNull(events);

            // 应有 meta + sources + error
            assertTrue(events.stream().anyMatch(e -> "error".equals(e.type())),
                "应有 error 事件");
            // done 不应出现
            assertTrue(events.stream().noneMatch(e -> "done".equals(e.type())),
                "失败时不应有 done 事件");

            ChatEvent errorEvent = events.stream()
                .filter(e -> "error".equals(e.type()))
                .findFirst().orElseThrow();
            assertNotNull(errorEvent.message());
            assertFalse(errorEvent.message().isBlank());

            System.out.println("  ✅ 错误降级: " + errorEvent.message());
        }

        @Test
        @DisplayName("限流拒绝 → error 事件")
        void rateLimitRejectionShouldBecomeErrorEvent() {
            doThrow(new ChatRateLimiter.RateLimitExceededException("请求过于频繁，每小时最多 20 次"))
                .when(rateLimiter).check(anyString(), anyString());

            Flux<ChatEvent> stream = ragChatService.chat(
                "问题", null, "test-visitor", "127.0.0.1");

            List<ChatEvent> events = stream.collectList().block(Duration.ofSeconds(120));
            assertNotNull(events);

            assertEquals(1, events.size());
            assertEquals("error", events.get(0).type());
            assertTrue(events.get(0).message().contains("过于频繁"));

            System.out.println("  ✅ 限流拒绝: " + events.get(0).message());
        }
    }

    // ============ 测试用 LLM 供应商 ============

    /**
     * 测试用 LLM 供应商，返回模拟的回答内容。
     *
     * <p>模拟 RAG 场景：回答中引用检索到的文章知识，
     * 以此来验证 Prompt 组装和检索上下文是否被正确传递。</p>
     */
    static class TestLLMProvider implements LLMProvider {

        private volatile boolean shouldFail = false;

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Flux<String> chatStream(ChatRequest request) {
            if (shouldFail) {
                return Flux.error(new RuntimeException("Test LLM failure"));
            }

            // 检查 system prompt 中是否有检索上下文
            String systemPrompt = request.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .findFirst()
                .orElse("");

            if (systemPrompt.contains("[来源") && systemPrompt.contains("JWT")) {
                // RAG 模式：引用检索到的知识
                return Flux.just(
                    "根据检索到的博客文章，",
                    "Spring Security 中的 JWT 认证",
                    "主要通过 OncePerRequestFilter 实现。",
                    "配置时需要继承该类并重写 doFilterInternal 方法。"
                );
            } else {
                // 通用聊天模式
                return Flux.just(
                    "你好！我是博客的 AI 助手，",
                    "可以回答关于博客文章内容的问题。",
                    "如果你有具体的技术问题，欢迎随时提问。"
                );
            }
        }

        @Override
        public String chat(ChatRequest request) {
            return chatStream(request)
                .collectList()
                .map(tokens -> String.join("", tokens))
                .block(Duration.ofSeconds(30));
        }
    }

    // ============ Mock Mapper 真正的 DB 写入操作 ============

    private void stubDocMapperDbOps(KnowledgeDocumentMapper docMapper) {
        // insert 真正写入 PG
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

        // findBySourceRef
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

        // updateById
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

        // deleteById
        doAnswer(inv -> {
            long id = inv.getArgument(0);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM t_knowledge_document WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
                return 1;
            }
        }).when(docMapper).deleteById(anyLong());
    }

    private void stubChunkMapperDbOps(KnowledgeChunkMapper chunkMapper) {
        // insert —— 只用基础列，避免 embedding_text 列在旧 PG 卷上不存在
        // （RagentSchemaMigration 只在 Spring 启动时执行 ALTER TABLE ADD COLUMN IF NOT EXISTS）
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

        // deleteByDocId
        doAnswer(inv -> {
            long docId = inv.getArgument(0);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM t_knowledge_chunk WHERE doc_id = ?")) {
                ps.setLong(1, docId);
                ps.executeUpdate();
                return 1;
            }
        }).when(chunkMapper).deleteByDocId(anyLong());
    }

    // ============ 工具方法 ============

    private static RagProperties.Provider createTestProviderConfig() {
        RagProperties.Provider p = new RagProperties.Provider();
        p.setEnabled(true);
        p.setPriority(1);
        p.setBaseUrl("test://local");
        p.setApiKey("");
        p.setChatModel("test-model");
        p.setChatTimeout(Duration.ofSeconds(120));
        return p;
    }

    /** 加载 API Key：先读环境变量，再读 .env 文件 */
    private static String loadApiKey() {
        String key = System.getenv("BAILIAN_API_KEY");
        if (key != null && !key.isBlank()) return key;

        try {
            java.nio.file.Path envFile = java.nio.file.Path.of(".env");
            if (java.nio.file.Files.exists(envFile)) {
                for (String line : java.nio.file.Files.readAllLines(envFile)) {
                    String trimmed = line.strip();
                    if (trimmed.startsWith("export ")) {
                        trimmed = trimmed.substring(7);
                    }
                    if (trimmed.startsWith("BAILIAN_API_KEY=")) {
                        key = trimmed.substring("BAILIAN_API_KEY=".length()).trim();
                        if (key.startsWith("\"") && key.endsWith("\"")) {
                            key = key.substring(1, key.length() - 1);
                        }
                        if (!key.isBlank()) return key;
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void ensureTestKnowledgeBase() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO t_knowledge_base (id, name, collection_name) " +
                 "VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING")) {
            ps.setLong(1, TEST_KB_ID);
            ps.setString(2, "test-kb-phase3");
            ps.setString(3, "test-collection-phase3");
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

    /** 简单的 DataSource 实现（与 Phase2EndToEndTest 模式一致） */
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
