package io.github.somehow.mysite.ragent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.core.PromptTemplate;
import io.github.somehow.mysite.ragent.core.ConversationManager;
import io.github.somehow.mysite.ragent.core.RetrievalEngine;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeChunkMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import io.github.somehow.mysite.ragent.chunking.MarkdownChunker;
import io.github.somehow.mysite.ragent.llm.LLMProvider;
import io.github.somehow.mysite.ragent.llm.RoutingLLMService;
import io.github.somehow.mysite.ragent.llm.embedding.BaiLianEmbeddingService;
import io.github.somehow.mysite.ragent.llm.embedding.EmbeddingService;
import io.github.somehow.mysite.ragent.llm.rerank.BaiLianRerankProvider;
import io.github.somehow.mysite.ragent.llm.model.ChatEvent;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.service.ChatRateLimiter;
import io.github.somehow.mysite.ragent.service.KnowledgeDocumentService;
import io.github.somehow.mysite.ragent.service.RagChatService;
import io.github.somehow.mysite.ragent.core.intent.IntentClassifier;
import io.github.somehow.mysite.ragent.core.intent.IntentResult;
import io.github.somehow.mysite.ragent.core.rewrite.QueryRewriter;
import io.github.somehow.mysite.ragent.core.rewrite.QueryRewriter.RewriteResult;
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
        // syncArticle(article, kbId) 会先通过 selectById 加载知识库
        when(kbMapper.selectById(anyLong())).thenReturn(defaultKb);

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

        // 默认：不改写、自动分类为 KB_RETRIEVAL
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        when(queryRewriter.rewrite(anyString(), anyList()))
            .thenAnswer(inv -> RewriteResult.unchanged(inv.getArgument(0)));

        IntentClassifier intentClassifier = mock(IntentClassifier.class);
        IntentResult defaultIntent = IntentResult.builder()
            .type("KB_RETRIEVAL").targetKbId(null).confidence(0.8)
            .needsGuidance(false).reason("test").build();
        when(intentClassifier.classify(anyString(), anyList())).thenReturn(defaultIntent);

        // kbNameCache 惰性初始化需要 knowledgeBases
        RagProperties.KnowledgeBaseMeta kb1 = new RagProperties.KnowledgeBaseMeta();
        kb1.setId(1L);
        kb1.setName("技术博客");
        ragProps.setKnowledgeBases(List.of(kb1));

        PromptTemplate promptTemplate = new PromptTemplate(ragProps);

        // 构建 RagChatService
        ragChatService = new RagChatService(
            retrievalEngine, conversationManager,
            promptTemplate, routingLLMService,
            rateLimiter, ragProps,
            queryRewriter, intentClassifier);
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
                "JWT 过滤器怎么配置？", null, "test-visitor", "127.0.0.1", UserRole.ADMIN, List.of(TEST_KB_ID));

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
                "JWT 过滤器怎么配置？", null, "test-visitor", "127.0.0.1", UserRole.ADMIN, List.of(TEST_KB_ID));

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
                "今天天气怎么样？", null, "test-visitor", "127.0.0.1", UserRole.ADMIN, List.of(TEST_KB_ID));

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
                "测试问题", null, "test-visitor", "127.0.0.1", UserRole.ADMIN, List.of(TEST_KB_ID));

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
                .when(rateLimiter).check(anyString(), anyString(), eq(UserRole.ADMIN));

            Flux<ChatEvent> stream = ragChatService.chat(
                "问题", null, "test-visitor", "127.0.0.1", UserRole.ADMIN, List.of(TEST_KB_ID));

            List<ChatEvent> events = stream.collectList().block(Duration.ofSeconds(120));
            assertNotNull(events);

            assertEquals(1, events.size());
            assertEquals("error", events.get(0).type());
            assertTrue(events.get(0).message().contains("过于频繁"));

            System.out.println("  ✅ 限流拒绝: " + events.get(0).message());
        }
    }

    // ============ Part 3: Rerank 精排质量验证（真实 API）============

    /**
     * 2026-07-28 检索质量修复的回归验证。
     *
     * <p>修复前的三个问题：
     * <ol>
     *   <li>rerank 调用传空字符串 query，精排模型无法按 query↔doc 相关性打分，形同虚设</li>
     *   <li>向量粗排阈值 0.3 对中文 embedding 过宽松，无关内容混入候选</li>
     *   <li>rerank 的 relevance_score 替换向量分后不做二次过滤，
     *       34% 相关度的来源也会进 prompt / 前端 sources</li>
     * </ol>
     * 本 Part 用真实百炼 embedding + qwen3-rerank API 验证修复效果（生产同款配置）。</p>
     */
    @Nested
    @DisplayName("Part 3 — Rerank 精排 + 双阈值过滤（真实 API）")
    class RerankQuality {

        private RetrievalEngine rerankEngine;
        private BaiLianRerankProvider rerankProvider;

        @BeforeEach
        void setUpRerankEngine() {
            // 生产同款检索配置：topK=10 / rerankTopK=5 / 双阈值 0.5
            RagProperties props = new RagProperties();
            props.getRetrieval().setTopK(10);
            props.getRetrieval().setRerankTopK(5);
            props.getRetrieval().setScoreThreshold(0.5);
            props.getRetrieval().setRerankScoreThreshold(0.5);

            RagProperties.Provider bailian = new RagProperties.Provider();
            bailian.setEnabled(true);
            bailian.setApiKey(loadApiKey());
            bailian.setRerankModel("qwen3-rerank");
            props.getLlm().getProviders().put("bailian", bailian);

            rerankProvider = new BaiLianRerankProvider(props, new ObjectMapper());
            rerankEngine = new RetrievalEngine(
                vectorStore, embeddingService, rerankProvider, props);
        }

        /** 灌入 3 篇长文（保证 chunk 总数 > 5，真正触发 rerank API 而非截断降级） */
        private void seedCorpus() {
            ArticleDO jwt = new ArticleDO();
            jwt.setId(93001L);
            jwt.setTitle("Spring Security JWT 认证配置指南");
            jwt.setContent("""
                ## JWT 过滤器配置

                Spring Security 中的 JWT 认证主要通过 OncePerRequestFilter 实现。
                它可以确保每个请求只被过滤一次，避免在转发和包含时重复执行。

                ### 核心配置步骤

                1. 创建 JwtAuthenticationFilter 继承 OncePerRequestFilter
                2. 在 SecurityFilterChain 中注册过滤器
                3. 配置 permitAll 和 authenticated 路径

                ### 令牌解析与校验

                JwtService 负责令牌的生成与解析，内部使用 JJWT 库。
                解析时需要配置签名密钥，推荐使用 HS256 算法加 256 位以上密钥。
                校验流程包括：签名验证、过期时间检查、claims 完整性检查。
                任何一个环节失败都应返回 401 而不是 500。

                ### 注意事项

                过滤器必须注册在 UsernamePasswordAuthenticationFilter 之前，
                否则表单登录会先拦截请求。推荐使用 addFilterBefore 方法。
                无状态会话需要在 SessionManagement 中配置 STATELESS 策略。

                ### 双令牌刷新机制

                访问令牌有效期短（如 2 小时），刷新令牌有效期长（如 7 天）。
                访问令牌过期后，客户端用刷新令牌换取新的访问令牌，避免频繁登录。
                刷新令牌应存储在 HttpOnly Cookie 中，防止 XSS 窃取。
                刷新令牌一旦使用就应轮换作废旧的，防止重放攻击。

                ### 异常处理与响应规范

                认证失败统一由 AuthenticationEntryPoint 处理，返回 401 和标准错误码。
                授权失败由 AccessDeniedHandler 处理，返回 403。
                不要在过滤器里直接写响应体，应该委托给统一的异常处理组件，
                保证所有错误响应的 JSON 结构一致。
                """);

            ArticleDO redis = new ArticleDO();
            redis.setId(93002L);
            redis.setTitle("Redis 缓存最佳实践");
            redis.setContent("""
                ## 缓存策略

                Redis 常用于缓存热点数据，减少数据库压力。
                推荐使用旁路缓存模式（Cache Aside）：
                先读缓存，未命中再查数据库，并写回缓存。

                ### 过期时间与淘汰策略

                每个 key 都应设置 TTL，避免内存无限增长。
                热点数据 TTL 可以长一些（如 30 分钟），冷数据短一些。
                maxmemory-policy 推荐 allkeys-lru，内存满时淘汰最近最少使用的 key。

                ### 缓存三大问题

                缓存穿透：查询不存在的数据，绕过缓存直击数据库，可用布隆过滤器拦截。
                缓存击穿：热点 key 过期瞬间大量并发查库，可用互斥锁或逻辑过期。
                缓存雪崩：大量 key 同时过期，可在 TTL 上加随机抖动。

                ### 缓存与数据库一致性

                更新数据时推荐先更新数据库再删除缓存（Cache Aside 标准做法）。
                删除缓存失败时可以用消息队列重试，或者订阅 binlog 异步删除。
                强一致性要求高的场景可以用读写锁串行化同一 key 的更新，
                但多数业务接受秒级的最终一致。

                ### 大 key 与热 key 治理

                单个 value 超过 10KB 就算大 key，会阻塞单线程的 Redis 事件循环。
                大 key 要拆分：Hash 按字段分桶，List 按区间分段。
                热 key 可以在应用层加本地缓存（Caffeine）分摊读压力，
                或者把同一 key 复制多份加随机后缀分散到不同分片。
                """);

            ArticleDO mysql = new ArticleDO();
            mysql.setId(93003L);
            mysql.setTitle("MySQL 索引优化实战");
            mysql.setContent("""
                ## 索引基础

                InnoDB 使用 B+ 树组织索引，聚簇索引的叶子节点存整行数据，
                二级索引的叶子节点存主键值，因此回表查询需要两次 B+ 树查找。

                ### 联合索引与最左前缀

                联合索引 (a, b, c) 可以服务于 a、a+b、a+b+c 三种查询条件，
                但跳过最左列的查询（如只按 b 过滤）无法使用该索引。
                设计联合索引时应把等值查询列放前面，范围查询列放最后。

                ### 覆盖索引与回表

                如果查询列全部包含在索引中，就不需要回表，性能最好。
                EXPLAIN 结果中 Extra 列出现 Using index 即表示覆盖索引生效。
                高频查询可以考虑建覆盖索引来消除回表开销。

                ### 索引失效的常见场景

                对索引列做函数运算或隐式类型转换会导致索引失效，全表扫描。
                LIKE 以通配符开头（'%abc'）无法使用索引，后缀匹配可以。
                OR 连接的条件如果有一侧无索引，整个查询也可能放弃索引。
                优化器估算回表成本过高时，会主动选择全表扫描而非走索引。

                ### 慢查询排查流程

                开启 slow_query_log，把 long_query_time 设为 1 秒。
                用 EXPLAIN 分析执行计划，重点看 type、rows、Extra 三列。
                type 从好到差：const > eq_ref > ref > range > index > ALL，
                出现 ALL 就是全表扫描，通常是缺索引或索引失效的信号。
                """);

            ArticleDO tx = new ArticleDO();
            tx.setId(93004L);
            tx.setTitle("Spring 事务传播机制详解");
            tx.setContent("""
                ## 七种传播行为

                Spring 事务传播行为决定方法之间如何共享事务上下文。
                REQUIRED 是默认值：有事务就加入，没有就新建。
                REQUIRES_NEW 总是新建事务并挂起当前事务，适合审计日志等
                不允许被外层回滚影响的场景。NESTED 使用 savepoint 实现嵌套事务，
                内层回滚不影响外层，但外层回滚会连内层一起回滚。
                SUPPORTS 和 NOT_SUPPORTED 的区别在于是否挂起当前事务，
                MANDATORY 要求必须在事务中调用否则抛异常，NEVER 则相反。
                实际开发中 90% 的场景用默认 REQUIRED 就够了，
                只有明确需要隔离提交或嵌套回滚语义时才考虑其他行为。
                """);

            ArticleDO docker = new ArticleDO();
            docker.setId(93005L);
            docker.setTitle("Docker Compose 多服务编排实践");
            docker.setContent("""
                ## 服务依赖与健康检查

                docker-compose 的 depends_on 只保证启动顺序，不保证服务就绪。
                MySQL 容器启动后还需要几十秒初始化，应用立刻连接会失败。
                正确做法是给数据库配置 healthcheck（如 mysqladmin ping），
                应用服务用 depends_on 的 condition: service_healthy 等待健康检查通过。
                卷挂载初始化脚本（docker-entrypoint-initdb.d）可以在首次启动时
                自动建库建表，但注意该目录只在数据卷为空时执行一次，
                修改脚本后必须 docker compose down -v 清空卷才会重新执行。
                生产环境建议把初始化脚本纳入版本控制并配合迁移工具使用。
                """);

            ArticleDO vue = new ArticleDO();
            vue.setId(93006L);
            vue.setTitle("Vue 3 组合式 API 设计模式");
            vue.setContent("""
                ## Composable 的抽象原则

                组合式函数（composable）是把可复用逻辑抽取成 use 开头的函数。
                好的 composable 只关心自己的状态生命周期：在 onMounted 里订阅，
                在 onUnmounted 里清理，避免内存泄漏。useTheme、useToast、
                useChat 都是典型例子。状态用 ref 和 reactive 管理，
                返回时解构会丢失响应性，应该用 toRefs 包一层再返回。
                副作用密集的逻辑（SSE 连接、轮询、事件监听）最值得抽取，
                纯展示逻辑留在组件里反而更直观。命名上 use 前缀是社区约定，
                参数尽量接受 ref 类型以便响应式联动，而不是原始值。
                """);

            ArticleDO nginx = new ArticleDO();
            nginx.setId(93007L);
            nginx.setTitle("Nginx 反向代理与缓存配置");
            nginx.setContent("""
                ## 反向代理基础

                Nginx 作为反向代理时，location 块的 proxy_pass 把请求转发给后端。
                转发时要设置 Host、X-Real-IP、X-Forwarded-For 头，
                否则后端拿到的是代理地址而不是真实客户端信息。
                WebSocket 代理需要额外配置 Upgrade 和 Connection 头的转发。

                ### 静态资源缓存

                带内容 hash 的静态资源可以配置超长缓存：
                expires 1y 加 Cache-Control: public, immutable。
                文件名含 hash，内容变化文件名必然变化，长缓存永远安全。
                但入口 HTML 绝不能长缓存，否则部署后用户拿到的还是旧版资源引用，
                HTML 应该配置 no-cache，每次都带 ETag 协商，未变化返回 304。
                这一长一短的搭配是 SPA 部署缓存策略的行业标准做法。
                """);

            docService.syncArticle(jwt);
            docService.syncArticle(redis);
            docService.syncArticle(mysql);
            docService.syncArticle(tx);
            docService.syncArticle(docker);
            docService.syncArticle(vue);
            docService.syncArticle(nginx);
        }

        private int countChunks() throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM t_knowledge_chunk WHERE kb_id = ?")) {
                ps.setLong(1, TEST_KB_ID);
                ResultSet rs = ps.executeQuery();
                rs.next();
                return rs.getInt(1);
            }
        }

        @Test
        @DisplayName("相关问题：精排后所有来源 relevance ≥ 0.5 且 top-1 主题正确")
        void relevantQueryShouldReturnHighQualitySources() throws Exception {
            seedCorpus();
            int chunks = countChunks();
            System.out.println("  语料 chunk 总数: " + chunks);
            assumeTrue(chunks > 5, "chunk 数需 > 5 才能触发 rerank API，实际: " + chunks);

            String question = "JWT 过滤器怎么配置？";

            // 对照组：粗排原始向量分数
            float[] qe = embeddingService.embed(question);
            List<VectorStore.SearchResult> raw = vectorStore.search(qe, 10, List.of(TEST_KB_ID));
            System.out.println("  【粗排原始分数】");
            raw.forEach(r -> System.out.println("    " + String.format("%.4f", r.score())
                + "  " + r.docTitle()));

            // 修复后的完整链路：向量阈值 0.5 → rerank(query 透传) → 精排阈值 0.5
            List<VectorStore.SearchResult> results =
                rerankEngine.retrieve(question, 5, List.of(TEST_KB_ID));

            System.out.println("  【精排最终来源】");
            results.forEach(r -> System.out.println("    " + String.format("%.4f", r.score())
                + "  " + r.docTitle()));

            assertFalse(results.isEmpty(), "相关问题应有检索结果");
            assertTrue(results.size() <= 5, "最终来源数不应超过 rerankTopK");
            assertTrue(results.stream().allMatch(r -> r.score() >= 0.5f),
                "精排后所有来源的 relevance_score 必须 ≥ 0.5，实际: "
                    + results.stream().map(r -> String.format("%.3f", r.score())).toList());
            assertTrue(results.get(0).docTitle().contains("JWT")
                    || results.get(0).content().contains("JWT"),
                "top-1 必须是 JWT 相关文章，实际: " + results.get(0).docTitle());
        }

        @Test
        @DisplayName("无关问题：双阈值过滤后零来源（修复前 34% 相关度也会被引用）")
        void irrelevantQueryShouldYieldNoSources() throws Exception {
            seedCorpus();
            int chunks = countChunks();
            System.out.println("  语料 chunk 总数: " + chunks);
            assumeTrue(chunks > 5, "chunk 数需 > 5 才能触发 rerank API，实际: " + chunks);

            String question = "红烧肉怎么做才好吃？";

            // 对照组：粗排原始向量分数（即使主题无关，中文 embedding 也可能给出中等分数）
            float[] qe = embeddingService.embed(question);
            List<VectorStore.SearchResult> raw = vectorStore.search(qe, 10, List.of(TEST_KB_ID));
            System.out.println("  【无关问题的粗排原始分数】");
            raw.forEach(r -> System.out.println("    " + String.format("%.4f", r.score())
                + "  " + r.docTitle()));

            // 单独验证精排层：即使粗排候选都放过，rerank 真实相关性也应远低于 0.5
            if (!raw.isEmpty()) {
                List<VectorStore.SearchResult> reranked =
                    rerankProvider.rerank(question, raw, Math.min(5, raw.size()));
                System.out.println("  【rerank 真实相关性（query 透传后）】");
                reranked.forEach(r -> System.out.println("    " + String.format("%.4f", r.score())
                    + "  " + r.docTitle()));
                assertTrue(reranked.stream().allMatch(r -> r.score() < 0.5f),
                    "无关问题的 rerank relevance 应全部 < 0.5（证明精排层能识别不相关）");
            }

            // 完整链路：向量阈值 + 精排阈值双重过滤后应为空
            List<VectorStore.SearchResult> results =
                rerankEngine.retrieve(question, 5, List.of(TEST_KB_ID));
            assertTrue(results.isEmpty(),
                "无关问题经双阈值过滤后不应产生任何来源，实际: " + results.size()
                    + " 条，分数: " + results.stream().map(r -> String.format("%.3f", r.score())).toList());
            System.out.println("  ✅ 无关问题最终来源数: 0（修复前此类查询会返回多条低分来源）");
        }

        /** 灌入 3 篇同主题 JWT 文章 + 1 篇干扰文章，保证多个候选能过向量阈值 */
        private void seedJwtFamily() {
            ArticleDO filter = new ArticleDO();
            filter.setId(94001L);
            filter.setTitle("Spring Security JWT 过滤器配置指南");
            filter.setContent("""
                ## JWT 过滤器配置

                Spring Security 中的 JWT 认证主要通过 OncePerRequestFilter 实现。
                创建 JwtAuthenticationFilter 继承 OncePerRequestFilter，
                重写 doFilterInternal 方法：从请求头提取 Bearer 令牌，
                调用 JwtService 校验签名和过期时间，校验通过后
                把 Authentication 写入 SecurityContextHolder。
                过滤器必须用 addFilterBefore 注册在
                UsernamePasswordAuthenticationFilter 之前，
                并在 SessionManagement 中配置 STATELESS 无状态会话策略，
                否则 Spring Security 仍会尝试创建 HttpSession。
                """);

            ArticleDO refresh = new ArticleDO();
            refresh.setId(94002L);
            refresh.setTitle("JWT 双令牌刷新机制实现");
            refresh.setContent("""
                ## 双令牌设计

                JWT 双令牌机制用短效访问令牌加长效刷新令牌解决安全与体验的平衡。
                访问令牌有效期设为 2 小时，只携带用户 ID 和角色等必要 claims；
                刷新令牌有效期 7 天，仅用于换取新的访问令牌。
                刷新接口校验刷新令牌有效后签发新访问令牌，
                同时轮换刷新令牌——旧的立即作废，防止被截获后重放。
                刷新令牌应放在 HttpOnly 加 Secure 的 Cookie 里，
                让前端脚本无法读取，从根本上防 XSS 窃取。
                """);

            ArticleDO validate = new ArticleDO();
            validate.setId(94003L);
            validate.setTitle("JWT 令牌校验与异常处理");
            validate.setContent("""
                ## 令牌校验流程

                JwtService 的校验分三步：先用签名密钥验证签名完整性，
                再检查 exp 声明是否过期，最后校验 claims 必填字段。
                JJWT 库解析时会自动抛 ExpiredJwtException 和
                SignatureException 等异常，过滤器里要分类捕获：
                过期返回 401 并带 TOKEN_EXPIRED 错误码让前端触发刷新流程，
                签名错误直接拒绝。任何认证失败都交给 AuthenticationEntryPoint
                统一响应，不要在过滤器里零散地写 response，
                保证错误 JSON 结构与全站接口规范一致。
                """);

            ArticleDO distractor = new ArticleDO();
            distractor.setId(94004L);
            distractor.setTitle("Redis 缓存最佳实践");
            distractor.setContent("""
                ## 缓存策略

                Redis 常用于缓存热点数据，减少数据库压力。
                推荐使用旁路缓存模式（Cache Aside）：
                先读缓存，未命中再查数据库，并写回缓存。
                每个 key 都应设置 TTL，避免内存无限增长，
                maxmemory-policy 推荐 allkeys-lru。
                """);

            docService.syncArticle(filter);
            docService.syncArticle(refresh);
            docService.syncArticle(validate);
            docService.syncArticle(distractor);
        }

        @Test
        @DisplayName("集成链路：query 透传使 rerank API 真实生效（修复前传空串精排失效）")
        void engineRetrieveShouldInvokeRealRerankWithQuery() throws Exception {
            seedJwtFamily();

            // rerankTopK=2：3 个 JWT 候选过 0.5 向量阈值后 > 2，必触发 rerank API
            RagProperties props = new RagProperties();
            props.getRetrieval().setTopK(10);
            props.getRetrieval().setRerankTopK(2);
            props.getRetrieval().setScoreThreshold(0.5);
            props.getRetrieval().setRerankScoreThreshold(0.5);
            RagProperties.Provider bailian = new RagProperties.Provider();
            bailian.setEnabled(true);
            bailian.setApiKey(loadApiKey());
            bailian.setRerankModel("qwen3-rerank");
            props.getLlm().getProviders().put("bailian", bailian);

            BaiLianRerankProvider provider = new BaiLianRerankProvider(props, new ObjectMapper());
            RetrievalEngine engine = new RetrievalEngine(
                vectorStore, embeddingService, provider, props);

            String question = "JWT 过滤器怎么配置？";

            // 前提检查：过向量阈值的候选数需 > rerankTopK，rerank API 才会被调用
            float[] qe = embeddingService.embed(question);
            List<VectorStore.SearchResult> overThreshold = vectorStore
                .search(qe, 10, List.of(TEST_KB_ID)).stream()
                .filter(r -> r.score() >= 0.5f).toList();
            System.out.println("  【过 0.5 向量阈值的候选】");
            overThreshold.forEach(r -> System.out.println("    "
                + String.format("%.4f", r.score()) + "  " + r.docTitle()));
            assumeTrue(overThreshold.size() > 2,
                "需 >2 个候选过阈值才能触发 rerank API，实际: " + overThreshold.size());

            // 基准：直接调 rerank（query 透传）
            List<VectorStore.SearchResult> direct = provider.rerank(question, overThreshold, 2);

            // 集成链路：engine.retrieve 内部的 rerank 调用
            List<VectorStore.SearchResult> viaEngine =
                engine.retrieve(question, 2, List.of(TEST_KB_ID));

            System.out.println("  【直接 rerank 基准】");
            direct.forEach(r -> System.out.println("    "
                + String.format("%.4f", r.score()) + "  " + r.docTitle()));
            System.out.println("  【集成链路结果】");
            viaEngine.forEach(r -> System.out.println("    "
                + String.format("%.4f", r.score()) + "  " + r.docTitle()));

            assertEquals(direct.size(), viaEngine.size(), "结果数应与基准一致");
            for (int i = 0; i < viaEngine.size(); i++) {
                assertEquals(direct.get(i).chunkId(), viaEngine.get(i).chunkId(),
                    "排序应与基准一致");
                // 分数一致 = 分数来自 rerank API 的 relevance_score（而非截断保留的向量分）
                assertEquals(direct.get(i).score(), viaEngine.get(i).score(), 5e-3,
                    "分数应与 rerank API 基准一致（证明集成链路真调了 rerank 而非向量截断）");
            }
            assertTrue(viaEngine.stream().allMatch(r -> r.score() >= 0.5f),
                "精排后所有来源 relevance ≥ 0.5");
            assertTrue(viaEngine.stream().allMatch(r ->
                    r.docTitle().contains("JWT") || r.content().contains("JWT")),
                "top-2 应全部是 JWT 主题文章");
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
