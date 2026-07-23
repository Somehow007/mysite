package io.github.somehow.mysite.ragent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.embedding.BaiLianEmbeddingService;
import io.github.somehow.mysite.ragent.llm.embedding.EmbeddingService;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.llm.provider.BaiLianProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 百炼 API 真实调用集成测试。
 *
 * 运行条件：设置环境变量 BAILIAN_API_KEY。
 * 不加 @Disabled —— @BeforeAll 里用 assumeTrue 检查，没有 key 就自动跳过。
 */
@DisplayName("百炼 API 真实调用集成测试")
class BaiLianIntegrationTest {

    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String CHAT_MODEL = "qwen3-max";
    private static final String EMBEDDING_MODEL = "text-embedding-v4";

    private static String apiKey;
    private static BaiLianProvider chatProvider;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        apiKey = System.getenv("BAILIAN_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
            "跳过：未设置 BAILIAN_API_KEY 环境变量");

        objectMapper = new ObjectMapper();
        chatProvider = new BaiLianProvider(BASE_URL, apiKey, CHAT_MODEL,
            Duration.ofSeconds(120), objectMapper);
    }

    // ============ DEBUG：打印百炼原始响应 ============

    @Test
    @DisplayName("DEBUG: 直接打印百炼 chat API 原始响应（非流式 + 流式）")
    void debugRawApiResponse() {
        apiKey = "sk-58d0aa5347a242e2b5da0bccdd6d3dc4";
        WebClient rawClient = WebClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();

        // 1) 非流式调用 — 最简单，先确认 API 本身通不通
        System.out.println("\n=== 非流式 (stream=false) ===");
        String nonStreamResp = rawClient.post()
            .uri("/chat/completions")
            .bodyValue(Map.of(
                "model", CHAT_MODEL,
                "messages", List.of(
                    Map.of("role", "user", "content", "说一个词")
                ),
                "stream", false
            ))
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(30));
        System.out.println("非流式原始响应:\n" + nonStreamResp);

        // 2) 流式调用 — 看每个 chunk 长什么样
        System.out.println("\n=== 流式 (stream=true) ===");
        List<String> rawChunks = rawClient.post()
            .uri("/chat/completions")
            .bodyValue(Map.of(
                "model", CHAT_MODEL,
                "messages", List.of(
                    Map.of("role", "user", "content", "说一个词")
                ),
                "stream", true
            ))
            .retrieve()
            .bodyToFlux(String.class)
            .collectList()
            .block(Duration.ofSeconds(30));
        System.out.println("流式 chunk 数: " + (rawChunks != null ? rawChunks.size() : 0));
        if (rawChunks != null) {
            for (int i = 0; i < Math.min(rawChunks.size(), 5); i++) {
                System.out.println("  chunk[" + i + "]: " + rawChunks.get(i).replace("\n", "\\n"));
            }
        }
    }

    // ==================== Chat 流式调用 ====================

    @Nested
    @DisplayName("chatStream — 流式对话")
    class ChatStream {

        @Test
        @DisplayName("最简对话：问好 → 拿到非空流式回复")
        void shouldGetStreamingResponse() {
            ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                    ChatMessage.system("你是一个有帮助的助手，用中文回复"),
                    ChatMessage.user("你好，请用一句话介绍你自己")
                ))
                .temperature(0.7)
                .maxTokens(200)
                .build();

            StringBuilder fullResponse = new StringBuilder();
            chatProvider.chatStream(request)
                .doOnNext(token -> System.out.print(token))
                .doOnNext(fullResponse::append)
                .collectList()
                .map(tokens -> String.join("", tokens))
                .block(Duration.ofSeconds(60));

            System.out.println();
            assertNotNull(fullResponse.toString());
            assertFalse(fullResponse.toString().isBlank(),
                "百炼应返回非空流式回复");
            assertTrue(fullResponse.toString().length() > 5,
                "回复应至少有几个字符，实际: " + fullResponse);
        }

        @Test
        @DisplayName("带 system prompt 的多轮对话 → 回复与上下文一致")
        void shouldRespectSystemPrompt() {
            ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                    ChatMessage.system("你的名字叫 MySite 助手。每次回复必须以「MySite 助手：」开头。"),
                    ChatMessage.user("你叫什么名字？")
                ))
                .temperature(0.3)
                .maxTokens(100)
                .build();

            String fullResponse = chatProvider.chatStream(request)
                .collectList()
                .map(tokens -> String.join("", tokens))
                .block(Duration.ofSeconds(60));

            System.out.println("回复: " + fullResponse);
            assertNotNull(fullResponse);
            assertTrue(fullResponse.contains("MySite"),
                "回复应包含 system prompt 中指定的名字，实际: " + fullResponse);
        }
    }

    // ==================== Chat 非流式调用 ====================

    @Nested
    @DisplayName("chat — 非流式对话")
    class ChatNonStream {

        @Test
        @DisplayName("同步调用 → 返回完整回复字符串")
        void shouldReturnFullResponse() {
            ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                    ChatMessage.user("请用三句话介绍 Spring Boot")
                ))
                .temperature(0.7)
                .maxTokens(300)
                .build();

            String response = chatProvider.chat(request);

            System.out.println("非流式回复: " + response);
            assertNotNull(response);
            assertFalse(response.isBlank());
            assertTrue(response.length() > 20,
                "回复应有一定长度，实际: " + response.length());
        }
    }

    // ==================== Embedding 真实调用 ====================

    @Nested
    @DisplayName("embed / embedBatch — 向量嵌入")
    class Embedding {

        @Test
        @DisplayName("单条文本嵌入 → 返回 float[1024]")
        void shouldReturn1024DimVector() {
            EmbeddingService embeddingService = createEmbeddingService();

            float[] vec = embeddingService.embed("今天天气真好，适合出去散步");

            assertNotNull(vec);
            assertEquals(1024, vec.length,
                "text-embedding-v4 应返回 1024 维向量");
            for (int i = 0; i < Math.min(10, vec.length); i++) {
                assertTrue(vec[i] >= -2.0f && vec[i] <= 2.0f,
                    "向量值应在合理范围内，vec[" + i + "]=" + vec[i]);
            }

            System.out.println("向量维度: " + vec.length);
            System.out.println("前 5 个值: " +
                vec[0] + ", " + vec[1] + ", " + vec[2] + ", " + vec[3] + ", " + vec[4]);
        }

        @Test
        @DisplayName("语义相近的文本 → 向量相似度更高")
        void semanticallySimilarTextsShouldBeCloser() {
            EmbeddingService embeddingService = createEmbeddingService();

            float[] vec1 = embeddingService.embed("Java 是一门面向对象的编程语言");
            float[] vec2 = embeddingService.embed("Java 由 Sun Microsystems 公司开发");
            float[] vec3 = embeddingService.embed("今天晚上吃火锅还是烧烤");

            double sim12 = cosineSimilarity(vec1, vec2);
            double sim13 = cosineSimilarity(vec1, vec3);

            System.out.println("相似度(Java1 vs Java2): " + String.format("%.4f", sim12));
            System.out.println("相似度(Java1 vs 火锅):   " + String.format("%.4f", sim13));

            assertTrue(sim12 > sim13,
                "语义相近的文本相似度应更高。Java vs Java=" +
                String.format("%.4f", sim12) + ", Java vs 火锅=" +
                String.format("%.4f", sim13));
        }

        @Test
        @DisplayName("批量嵌入 12 条 → 应自动分 2 批调用（10+2）")
        void shouldBatchEmbed12Texts() {
            EmbeddingService embeddingService = createEmbeddingService();

            List<String> texts = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                texts.add("这是第 " + i + " 段测试文本，用于验证百炼 embedding 批量调用。");
            }

            List<float[]> results = embeddingService.embedBatch(texts);

            assertEquals(12, results.size(), "应返回 12 个向量");
            for (int i = 0; i < 12; i++) {
                assertNotNull(results.get(i), "第 " + i + " 个向量不应为 null");
                assertEquals(1024, results.get(i).length,
                    "第 " + i + " 个向量维度应为 1024");
            }

            System.out.println("批量嵌入 12 条成功，每个向量维度: " + results.get(0).length);
        }
    }

    // ==================== 工具方法 ====================

    private static EmbeddingService createEmbeddingService() {
        return new BaiLianEmbeddingService(
            WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build(),
            EMBEDDING_MODEL,
            objectMapper,
            10
        );
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
