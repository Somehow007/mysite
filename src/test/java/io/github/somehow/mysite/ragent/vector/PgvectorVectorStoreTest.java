package io.github.somehow.mysite.ragent.vector;

import com.pgvector.PGvector;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PgvectorVectorStore 集成测试。
 *
 * 需要本地 PostgreSQL + pgvector 运行中（docker compose up -d postgres）。
 * 如果连不上，所有测试自动跳过（assumeTrue）。
 *
 * 测试覆盖：
 *   - insert / search / deleteByDocId / deleteByKbId
 *   - kbId 过滤 vs 全库检索
 *   - 空结果
 */
@DisplayName("PgvectorVectorStore — pgvector 集成测试")
class PgvectorVectorStoreTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ragent";
    private static final String USER = "ragent";
    private static final String PASSWORD = "ragent123";

    private static DataSource dataSource;
    private PgvectorVectorStore store;

    @BeforeAll
    static void checkConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD)) {
                // 验证 pgvector 扩展是否安装
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT 1 FROM pg_extension WHERE extname='vector'")) {
                    assumeTrue(rs.next(),
                        "跳过：pgvector 扩展未安装");
                }
            }
            // 简单 DataSource 实现
            dataSource = new DataSource();
        } catch (Exception e) {
            assumeTrue(false, "跳过：无法连接 PostgreSQL — " + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        store = new PgvectorVectorStore(dataSource);
        // 清理上次测试的数据
        store.deleteByKbId(99999L);
    }

    @AfterEach
    void tearDown() {
        store.deleteByKbId(99999L);
    }

    // ==================== insert + search ====================

    @Nested
    @DisplayName("insert 与 search")
    class InsertAndSearch {

        @Test
        @DisplayName("插入 1 条向量 → search 能检索到")
        void insertOneAndSearch() {
            // 先插入 chunks（PgvectorVectorStore.search JOIN t_knowledge_chunk 和 t_knowledge_document）
            insertTestChunkAndDocument(10001L, 99999L, "测试文档",
                "Spring Security JWT 过滤器配置方法详解");

            float[] embedding = randomVector(1024);
            store.insert(List.of(new VectorStore.VectorEntry(10001L, 99999L, embedding, "text-embedding-v4")));

            List<VectorStore.SearchResult> results = store.search(embedding, 5, null);

            assertEquals(1, results.size());
            assertEquals(10001L, results.get(0).chunkId());
            assertEquals("测试文档", results.get(0).docTitle());
            assertTrue(results.get(0).score() > 0.9f,
                "相同向量的 cosine 相似度应接近 1，实际: " + results.get(0).score());
        }

        @Test
        @DisplayName("插入 3 条 → search 按相似度降序")
        void searchShouldOrderBySimilarity() {
            // 插入 3 个 chunk，分别对应不同的向量
            insertTestChunkAndDocument(20001L, 99999L, "文档A", "Java 并发编程");
            insertTestChunkAndDocument(20002L, 99999L, "文档B", "Spring Boot 入门");
            insertTestChunkAndDocument(20003L, 99999L, "文档C", "Redis 缓存实战");

            float[] queryVec = randomVector(1024);

            // 第 1 个与查询完全相同 → 最相似
            store.insert(List.of(new VectorStore.VectorEntry(20001L, 99999L, queryVec, "v4")));
            // 第 2 个与查询不同 → 排后面
            store.insert(List.of(new VectorStore.VectorEntry(20002L, 99999L, randomVector(1024), "v4")));
            // 第 3 个与查询不同 → 排后面
            store.insert(List.of(new VectorStore.VectorEntry(20003L, 99999L, randomVector(1024), "v4")));

            List<VectorStore.SearchResult> results = store.search(queryVec, 5, null);

            assertTrue(results.size() >= 3);
            assertEquals(20001L, results.get(0).chunkId(),
                "相同向量应排第一");
            assertTrue(results.get(0).score() > results.get(1).score(),
                "相似度应递减");
        }

        @Test
        @DisplayName("search topK 限制生效")
        void searchShouldRespectTopK() {
            for (long chunkId = 30001L; chunkId <= 30010L; chunkId++) {
                insertTestChunkAndDocument(chunkId, 99999L, "批量文档", "测试内容 " + chunkId);
                store.insert(List.of(new VectorStore.VectorEntry(
                    chunkId, 99999L, randomVector(1024), "v4")));
            }

            float[] queryVec = randomVector(1024);
            List<VectorStore.SearchResult> results = store.search(queryVec, 3, null);

            assertEquals(3, results.size(), "应只返回 top 3");
        }
    }

    // ==================== kbId 过滤 ====================

    @Nested
    @DisplayName("kbId 过滤")
    class KbIdFiltering {

        @Test
        @DisplayName("传 kbId → 只返回该知识库的结果")
        void searchWithKbIdShouldFilter() {
            long kb1 = 99999L;
            long kb2 = 88888L;

            insertTestChunkAndDocument(40001L, kb1, "KB1文档", "知识库1的内容");
            insertTestChunkAndDocument(40002L, kb2, "KB2文档", "知识库2的内容");

            float[] vec = randomVector(1024);
            store.insert(List.of(new VectorStore.VectorEntry(40001L, kb1, vec, "v4")));
            store.insert(List.of(new VectorStore.VectorEntry(40002L, kb2, vec, "v4")));

            List<VectorStore.SearchResult> results = store.search(vec, 10, kb1);

            // 清理 kb2
            try {
                assertTrue(results.stream().allMatch(r -> r.kbId().equals(kb1)),
                    "所有结果应属于 kb1");
            } finally {
                store.deleteByKbId(kb2);
            }
        }

        @Test
        @DisplayName("传 null → 全库检索")
        void searchWithNullKbIdShouldReturnAll() {
            long kb1 = 99999L;
            long kb2 = 88888L;

            insertTestChunkAndDocument(50001L, kb1, "文档1", "内容1");
            insertTestChunkAndDocument(50002L, kb2, "文档2", "内容2");

            float[] vec = randomVector(1024);
            store.insert(List.of(new VectorStore.VectorEntry(50001L, kb1, vec, "v4")));
            store.insert(List.of(new VectorStore.VectorEntry(50002L, kb2, vec, "v4")));

            List<VectorStore.SearchResult> results = store.search(vec, 10, null);

            assertTrue(results.size() >= 2, "null kbId 应返回所有知识库的结果");

            // 清理
            store.deleteByKbId(kb2);
        }
    }

    // ==================== delete ====================

    @Nested
    @DisplayName("删除操作")
    class Delete {

        @Test
        @DisplayName("deleteByDocId → 删除后 search 不到")
        void deleteByDocIdShouldRemoveVectors() {
            insertTestChunkAndDocument(60001L, 99999L, "待删文档", "这段内容会被删除");
            insertTestChunkAndDocument(60002L, 99999L, "待删文档", "第二段内容");

            float[] vec = randomVector(1024);
            store.insert(List.of(new VectorStore.VectorEntry(60001L, 99999L, vec, "v4")));
            store.insert(List.of(new VectorStore.VectorEntry(60002L, 99999L, vec, "v4")));

            // 删除 60001 对应的 doc（注意 deleteByDocId 通过 chunk 表关联删除 vector）
            // 这里用直接 SQL 删掉 chunk 和 document 记录来模拟
            store.deleteByDocId(60001L);  // 这删的是 chunk_id IN (SELECT id FROM t_knowledge_chunk WHERE doc_id=60001)

            // 验证：60002 还在
            List<VectorStore.SearchResult> results = store.search(vec, 10, null);
            assertTrue(results.stream().noneMatch(r -> r.chunkId().equals(60001L)),
                "已删除的 chunk 不应出现在结果中");
        }

        @Test
        @DisplayName("deleteByKbId → 删除整个知识库的所有向量")
        void deleteByKbIdShouldRemoveAllInKb() {
            long testKbId = 77777L;
            insertTestChunkAndDocument(70001L, testKbId, "KB文档", "内容A");
            insertTestChunkAndDocument(70002L, testKbId, "KB文档", "内容B");

            float[] vec = randomVector(1024);
            store.insert(List.of(new VectorStore.VectorEntry(70001L, testKbId, vec, "v4")));
            store.insert(List.of(new VectorStore.VectorEntry(70002L, testKbId, vec, "v4")));

            store.deleteByKbId(testKbId);

            List<VectorStore.SearchResult> results = store.search(vec, 10, null);
            assertTrue(results.stream().noneMatch(r -> r.kbId().equals(testKbId)),
                "删除后该 kbId 不应有结果");
        }
    }

    // ==================== 边界 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("search 无匹配结果 → 返回空列表")
        void searchWithNoMatchShouldReturnEmpty() {
            float[] vec = randomVector(1024);
            List<VectorStore.SearchResult> results = store.search(vec, 5, 99999L);
            // 99999 知识库应该没有数据（setUp 已清理）
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("insert 空列表 → 不报错")
        void insertEmptyListShouldNotFail() {
            assertDoesNotThrow(() -> store.insert(List.of()));
        }
    }

    // ==================== 工具方法 ====================

    /** 生成随机 1024 维向量 */
    private static float[] randomVector(int dim) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (Math.random() * 2 - 1);  // [-1, 1]
        }
        return v;
    }

    /** 插入测试用的 chunk 和 document 记录（search SQL 需要 JOIN 这两张表） */
    private void insertTestChunkAndDocument(long chunkId, long kbId, String title, String content) {
        try (Connection conn = dataSource.getConnection()) {
            // 检查 document 是否存在
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM t_knowledge_document WHERE id = ?"
            )) {
                ps.setLong(1, chunkId);  // 用 chunkId 做 docId 简化测试
                if (!ps.executeQuery().next()) {
                    // 插入 document
                    try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO t_knowledge_document (id, kb_id, title, source_type, source_ref, status) " +
                        "VALUES (?, ?, ?, 'TEST', ?, 'READY') ON CONFLICT DO NOTHING"
                    )) {
                        ins.setLong(1, chunkId);
                        ins.setLong(2, kbId);
                        ins.setString(3, title);
                        ins.setString(4, "test-" + chunkId);
                        ins.executeUpdate();
                    }
                }
            }
            // 插入或替换 chunk
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO t_knowledge_chunk (id, doc_id, kb_id, chunk_index, content) " +
                "VALUES (?, ?, ?, 0, ?) ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content"
            )) {
                ps.setLong(1, chunkId);
                ps.setLong(2, chunkId);  // doc_id = chunk_id
                ps.setLong(3, kbId);
                ps.setString(4, content);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert test data", e);
        }
    }

    /** 简单的测试用 DataSource */
    private static class DataSource implements javax.sql.DataSource {
        @Override public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
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
