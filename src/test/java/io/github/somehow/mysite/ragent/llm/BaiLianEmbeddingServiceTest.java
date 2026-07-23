package io.github.somehow.mysite.ragent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.embedding.BaiLianEmbeddingService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaiLianEmbeddingService 单元测试。
 *
 * 覆盖验收清单：
 *   ✅ embed() 返回正确维度的向量
 *   ✅ embedBatch() 超过单批上限时分批调用且结果顺序正确
 *
 * 测试方式：
 *   - parseEmbeddingResponse：直接测 JSON 解析（不依赖网络）
 *   - embed / embedBatch：用 MockWebServer 模拟百炼 API 响应来测完整链路
 */
@DisplayName("BaiLianEmbeddingService")
class BaiLianEmbeddingServiceTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MockWebServer mockServer;
    private BaiLianEmbeddingService service;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        WebClient webClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build();

        service = new BaiLianEmbeddingService(webClient, "text-embedding-v4", objectMapper, 10);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    // ==================== parseEmbeddingResponse 单元测试 ====================

    @Nested
    @DisplayName("parseEmbeddingResponse — JSON 解析")
    class ParseResponse {

        @Test
        @DisplayName("单条嵌入：返回正确的 1024 维向量")
        void shouldParseSingleEmbedding() {
            String json = buildEmbeddingResponse(new float[][]{ makeVec(1024, 0.1f) });

            List<float[]> results = service.parseEmbeddingResponse(json);

            assertEquals(1, results.size());
            assertEquals(1024, results.get(0).length);
            assertEquals(0.1f, results.get(0)[0], 0.0001f);
        }

        @Test
        @DisplayName("批量嵌入：返回与输入数量一致的向量")
        void shouldParseBatchEmbedding() {
            float[][] vecs = { makeVec(1024, 0.1f), makeVec(1024, 0.2f), makeVec(1024, 0.3f) };
            String json = buildEmbeddingResponse(vecs);

            List<float[]> results = service.parseEmbeddingResponse(json);

            assertEquals(3, results.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(1024, results.get(i).length);
            }
            // 验证顺序
            assertEquals(0.1f, results.get(0)[0], 0.0001f);
            assertEquals(0.2f, results.get(1)[0], 0.0001f);
            assertEquals(0.3f, results.get(2)[0], 0.0001f);
        }

        @Test
        @DisplayName("缺少 data 字段 -> RuntimeException")
        void shouldThrowWhenDataMissing() {
            String json = "{\"object\":\"list\"}";

            assertThrows(RuntimeException.class, () -> service.parseEmbeddingResponse(json));
        }

        @Test
        @DisplayName("data 不是数组 -> RuntimeException")
        void shouldThrowWhenDataIsNotArray() {
            String json = "{\"data\":\"not an array\"}";

            assertThrows(RuntimeException.class, () -> service.parseEmbeddingResponse(json));
        }

        @Test
        @DisplayName("非法 JSON -> RuntimeException")
        void shouldThrowOnInvalidJson() {
            assertThrows(RuntimeException.class,
                () -> service.parseEmbeddingResponse("{{{ bad json"));
        }
    }

    // ==================== embed / embedBatch 集成测试（MockWebServer） ====================

    @Nested
    @DisplayName("embed / embedBatch — 通过 MockWebServer 测试完整链路")
    class EmbedWithMockServer {

        @Test
        @DisplayName("embed() 单条文本 -> 返回 1024 维向量")
        void embedShouldReturn1024DimVector() {
            mockServer.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(new float[][]{ makeVec(1024, 0.5f) }))
                .addHeader("Content-Type", "application/json"));

            float[] vec = service.embed("今天天气真好");

            assertNotNull(vec);
            assertEquals(1024, vec.length);
            assertEquals(0.5f, vec[0], 0.0001f);
        }

        @Test
        @DisplayName("embedBatch() 少于 maxBatchSize -> 一次调用完成")
        void embedBatchShouldCallOnceWhenUnderLimit() {
            // 5 条 -> 不超过 10，只需一次调用
            float[][] vecs = new float[5][];
            for (int i = 0; i < 5; i++) vecs[i] = makeVec(1024, 0.1f * (i + 1));

            mockServer.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(vecs))
                .addHeader("Content-Type", "application/json"));

            List<String> texts = List.of("a", "b", "c", "d", "e");
            List<float[]> results = service.embedBatch(texts);

            assertEquals(5, results.size());
            assertEquals(1, mockServer.getRequestCount(), "应该只发一次 HTTP 请求");
        }

        @Test
        @DisplayName("embedBatch() 超过 maxBatchSize -> 分批调用，结果顺序正确")
        void embedBatchShouldSplitWhenOverLimit() {
            // 25 条 -> 10 + 10 + 5，三次调用
            int total = 25;
            float[][] batch1 = new float[10][];
            float[][] batch2 = new float[10][];
            float[][] batch3 = new float[5][];
            for (int i = 0; i < 10; i++) { batch1[i] = makeVec(1024, i + 1); }
            for (int i = 0; i < 10; i++) { batch2[i] = makeVec(1024, i + 11); }
            for (int i = 0; i < 5; i++)  { batch3[i] = makeVec(1024, i + 21); }

            mockServer.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(batch1)).addHeader("Content-Type", "application/json"));
            mockServer.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(batch2)).addHeader("Content-Type", "application/json"));
            mockServer.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(batch3)).addHeader("Content-Type", "application/json"));

            List<String> texts = new ArrayList<>();
            for (int i = 1; i <= total; i++) texts.add("text-" + i);

            List<float[]> results = service.embedBatch(texts);

            assertEquals(total, results.size(), "应返回全部 25 个向量");
            assertEquals(3, mockServer.getRequestCount(), "应发 3 次 HTTP 请求");

            // 验证顺序：第 1 个向量第一个元素 = 1，第 25 个 = 25
            assertEquals(1.0f, results.get(0)[0], 0.0001f);
            assertEquals(11.0f, results.get(10)[0], 0.0001f);
            assertEquals(21.0f, results.get(20)[0], 0.0001f);
            assertEquals(25.0f, results.get(24)[0], 0.0001f);
        }

        @Test
        @DisplayName("embedBatch() 刚好等于 maxBatchSize -> 一次调用")
        void embedBatchShouldCallOnceWhenExactLimit() {
            float[][] vecs = new float[10][];
            for (int i = 0; i < 10; i++) vecs[i] = makeVec(1024, i + 1);

            mockServer.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(vecs))
                .addHeader("Content-Type", "application/json"));

            List<String> texts = new ArrayList<>();
            for (int i = 0; i < 10; i++) texts.add("text-" + i);

            List<float[]> results = service.embedBatch(texts);

            assertEquals(10, results.size());
            assertEquals(1, mockServer.getRequestCount());
        }

        @Test
        @DisplayName("embedBatch() 空列表 -> 返回空列表，不发请求")
        void embedBatchShouldReturnEmptyForEmptyInput() {
            List<float[]> results = service.embedBatch(List.of());

            assertTrue(results.isEmpty());
            assertEquals(0, mockServer.getRequestCount());
        }

        @Test
        @DisplayName("API 返回 500 -> RuntimeException")
        void shouldThrowOnApiError() {
            mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":\"internal server error\"}"));

            assertThrows(RuntimeException.class, () -> service.embed("test"));
        }
    }

    // ==================== 工具方法 ====================

    /** 生成指定维度、所有元素同值的向量 */
    private static float[] makeVec(int dim, float value) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) v[i] = value;
        return v;
    }

    /** 构建 OpenAI 兼容的 embedding 响应 JSON */
    private static String buildEmbeddingResponse(float[][] vectors) {
        StringBuilder sb = new StringBuilder("{\"object\":\"list\",\"data\":[");
        for (int i = 0; i < vectors.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"object\":\"embedding\",\"index\":").append(i).append(",\"embedding\":[");
            float[] v = vectors[i];
            for (int j = 0; j < v.length; j++) {
                if (j > 0) sb.append(",");
                sb.append(v[j]);
            }
            sb.append("]}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
