package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AbstractOpenAiProvider 单元测试。
 *
 * 覆盖验收清单：
 *   ✅ chatStream() 能正确解析 SSE 事件流
 *
 * 通过测试专用子类来测试 extractDeltaContent + chatStream 管道逻辑。
 * JSON 字符串使用普通转义字符串（不用 text block）避免 """ 与 JSON 引号冲突。
 */
@DisplayName("AbstractOpenAiProvider SSE 解析")
class AbstractOpenAiProviderTest {

    private ObjectMapper objectMapper;
    private TestProvider provider;

    /**
     * 测试用具体实现：不发起真实 HTTP 请求，而是提供预置 SSE 行序列。
     */
    static class TestProvider extends AbstractOpenAiProvider {

        private final Flux<String> presetLines;

        TestProvider(ObjectMapper objectMapper, Flux<String> presetLines) {
            super("http://test.local/v1", "test-key", "test-model",
                  Duration.ofSeconds(5), objectMapper);
            this.presetLines = presetLines;
        }

        @Override
        public Flux<String> chatStream(ChatRequest request) {
            // 绕过真实 WebClient，测试 SSE 管道逻辑
            return presetLines
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6))
                .map(this::extractDeltaContent)
                .filter(content -> content != null && !content.isEmpty());
        }

    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ==================== extractDeltaContent 测试 ====================

    @Nested
    @DisplayName("extractDeltaContent — 从 SSE JSON 中提取 delta.content")
    class ExtractDeltaContent {

        @BeforeEach
        void setUp() {
            provider = new TestProvider(objectMapper, Flux.empty());
        }

        @Test
        @DisplayName("正常提取 content")
        void shouldExtractContent() {
            String json = "{\"choices\":[{\"delta\":{\"content\":\"你好\"},\"index\":0}]}";

            String result = provider.extractDeltaContent(json);

            assertEquals("你好", result);
        }

        @Test
        @DisplayName("逐 token 提取：模拟流式输出")
        void shouldExtractEachToken() {
            String[] sseLines = {
                "{\"choices\":[{\"delta\":{\"content\":\"Spring\"},\"index\":0}]}",
                "{\"choices\":[{\"delta\":{\"content\":\" Boot\"},\"index\":0}]}",
                "{\"choices\":[{\"delta\":{\"content\":\" 是\"},\"index\":0}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"一个\"},\"index\":0}]}",
                "{\"choices\":[{\"delta\":{\"content\":\" 框架\"},\"index\":0}]}"
            };

            StringBuilder full = new StringBuilder();
            for (String line : sseLines) {
                full.append(provider.extractDeltaContent(line));
            }

            assertEquals("Spring Boot 是一个 框架", full.toString());
        }

        @Test
        @DisplayName("finish_reason 无 content → 返回空字符串")
        void shouldReturnEmptyForFinishReason() {
            String json = "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\",\"index\":0}]}";

            String result = provider.extractDeltaContent(json);

            assertEquals("", result);
        }

        @Test
        @DisplayName("choices 为空数组 → 返回空字符串")
        void shouldReturnEmptyForEmptyChoices() {
            String json = "{\"choices\":[]}";

            String result = provider.extractDeltaContent(json);

            assertEquals("", result);
        }

        @Test
        @DisplayName("非法 JSON → 返回空字符串（不抛异常）")
        void shouldReturnEmptyForInvalidJson() {
            String result = provider.extractDeltaContent("not valid json {{{");

            assertEquals("", result);
        }

        @Test
        @DisplayName("空字符串 → 返回空字符串")
        void shouldReturnEmptyForEmptyString() {
            assertEquals("", provider.extractDeltaContent(""));
        }

        @Test
        @DisplayName("content 为 null → 返回空字符串")
        void shouldReturnEmptyForNullContent() {
            String json = "{\"choices\":[{\"delta\":{\"content\":null},\"index\":0}]}";

            String result = provider.extractDeltaContent(json);

            assertEquals("", result);
        }
    }

    // ==================== chatStream 管道测试 ====================

    @Nested
    @DisplayName("chatStream — SSE 事件流处理管道")
    class ChatStreamPipeline {

        @Test
        @DisplayName("完整 SSE 流 → 逐 token 输出，过滤 [DONE]")
        void shouldProcessFullSseStream() {
            Flux<String> sseLines = Flux.just(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"},\"index\":0}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"},\"index\":0}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"！\"},\"index\":0}]}",
                "data: [DONE]"
            );

            provider = new TestProvider(objectMapper, sseLines);

            StepVerifier.create(provider.chatStream(null))
                .expectNext("你")
                .expectNext("好")
                .expectNext("！")
                .verifyComplete();
        }

        @Test
        @DisplayName("只保留 data: 前缀的行，过滤心跳和 event: 行")
        void shouldFilterNonDataLines() {
            Flux<String> sseLines = Flux.just(
                ": heartbeat\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"index\":0}]}",
                "event: ping",
                "data: {\"choices\":[{\"delta\":{\"content\":\" there\"},\"index\":0}]}",
                "data: [DONE]"
            );

            provider = new TestProvider(objectMapper, sseLines);

            StepVerifier.create(provider.chatStream(null))
                .expectNext("Hi")
                .expectNext(" there")
                .verifyComplete();
        }

        @Test
        @DisplayName("空 content 的 token 被过滤不输出")
        void shouldFilterEmptyTokens() {
            Flux<String> sseLines = Flux.just(
                "data: {\"choices\":[{\"delta\":{\"content\":\"A\"},\"index\":0}]}",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]"
            );

            provider = new TestProvider(objectMapper, sseLines);

            StepVerifier.create(provider.chatStream(null))
                .expectNext("A")
                .verifyComplete();
        }

        @Test
        @DisplayName("多 choices（罕见但合法）→ 只取第一个")
        void shouldOnlyTakeFirstChoice() {
            Flux<String> sseLines = Flux.just(
                "data: {\"choices\":[{\"delta\":{\"content\":\"first\"},\"index\":0},{\"delta\":{\"content\":\"second\"},\"index\":1}]}",
                "data: [DONE]"
            );

            provider = new TestProvider(objectMapper, sseLines);

            StepVerifier.create(provider.chatStream(null))
                .expectNext("first")
                .verifyComplete();
        }
    }

    // ==================== chat() 非流式测试 ====================

    @Nested
    @DisplayName("chat() — 非流式收集")
    class NonStreamingChat {

        @Test
        @DisplayName("收集所有 token 拼接成完整字符串")
        void shouldCollectAllTokens() {
            Flux<String> sseLines = Flux.just(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"index\":0}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\" World\"},\"index\":0}]}",
                "data: [DONE]"
            );

            provider = new TestProvider(objectMapper, sseLines);

            String result = provider.chat(ChatRequest.of("test-model", "hi"));

            assertEquals("Hello World", result);
        }
    }
}
