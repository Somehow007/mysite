package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.core.PromptTemplate;
import io.github.somehow.mysite.ragent.core.memory.ConversationManager;
import io.github.somehow.mysite.ragent.core.retrieval.RetrievalEngine;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.*;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RagChatService — RAG 问答核心服务")
class RagChatServiceTest {

    private RetrievalEngine retrievalEngine;
    private ConversationManager conversationManager;
    private PromptTemplate promptTemplate;
    private RoutingLLMService routingLLMService;
    private ChatRateLimiter rateLimiter;
    private RagProperties properties;
    private RagChatService service;

    @BeforeEach
    void setUp() {
        retrievalEngine = mock(RetrievalEngine.class);
        conversationManager = mock(ConversationManager.class);
        promptTemplate = new PromptTemplate();
        routingLLMService = mock(RoutingLLMService.class);
        rateLimiter = mock(ChatRateLimiter.class);

        properties = new RagProperties();
        properties.getRetrieval().setRerankTopK(5);

        service = new RagChatService(retrievalEngine, conversationManager,
            promptTemplate, routingLLMService, rateLimiter, properties);
    }

    @Nested
    @DisplayName("事件序列完整性")
    class EventSequence {

        @Test
        @DisplayName("应发送 meta → sources → content → done 序列")
        void shouldSendCorrectEventSequence() {
            // Given
            ConversationDO conv = new ConversationDO();
            conv.setId(42L);

            when(conversationManager.getOrCreateConversation(null, "visitor-1", "测试问题"))
                .thenReturn(conv);
            when(conversationManager.loadHistory(42L)).thenReturn(List.of());
            when(retrievalEngine.retrieve("测试问题", 5)).thenReturn(List.of());
            when(routingLLMService.chatStream(any()))
                .thenReturn(Flux.just("Hello", " World"));

            // When
            Flux<ChatEvent> events = service.chat("测试问题", null, "visitor-1", "127.0.0.1");

            // Then
            StepVerifier.create(events)
                .expectNextMatches(e -> "meta".equals(e.type()) && e.conversationId() == 42L)
                .expectNextMatches(e -> "sources".equals(e.type()))
                .expectNextMatches(e -> "content".equals(e.type()) && "Hello".equals(e.delta()))
                .expectNextMatches(e -> "content".equals(e.type()) && " World".equals(e.delta()))
                .expectNextMatches(e -> "done".equals(e.type()))
                .verifyComplete();
        }

        @Test
        @DisplayName("新对话应回传 conversationId 在 meta 事件中")
        void newConversationShouldReturnIdInMeta() {
            ConversationDO conv = new ConversationDO();
            conv.setId(99L);

            when(conversationManager.getOrCreateConversation(null, "v", "q")).thenReturn(conv);
            when(conversationManager.loadHistory(99L)).thenReturn(List.of());
            when(retrievalEngine.retrieve(anyString(), anyInt())).thenReturn(List.of());
            when(routingLLMService.chatStream(any()))
                .thenReturn(Flux.just("ok"));

            Flux<ChatEvent> events = service.chat("q", null, "v", "127.0.0.1");

            StepVerifier.create(events)
                .expectNextMatches(e -> "meta".equals(e.type()) && e.conversationId() == 99L)
                .expectNextCount(3)  // sources + content + done
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("检索结果传递")
    class RetrievalResults {

        @Test
        @DisplayName("检索结果应在 sources 事件中携带")
        void sourcesShouldContainRetrievalResults() {
            ConversationDO conv = new ConversationDO();
            conv.setId(1L);

            List<SearchResult> results = List.of(
                new SearchResult(1L, 100L, "文章A", "内容片段", 0.9f, 1L),
                new SearchResult(2L, 100L, "文章A", "另一片段", 0.8f, 1L)
            );

            when(conversationManager.getOrCreateConversation(any(), any(), any())).thenReturn(conv);
            when(conversationManager.loadHistory(anyLong())).thenReturn(List.of());
            when(retrievalEngine.retrieve(anyString(), anyInt())).thenReturn(results);
            when(routingLLMService.chatStream(any())).thenReturn(Flux.just("答案"));

            Flux<ChatEvent> events = service.chat("q", 1L, "v", "127.0.0.1");

            StepVerifier.create(events)
                .expectNextMatches(e -> "meta".equals(e.type()))
                .expectNextMatches(e -> "sources".equals(e.type())
                    && e.sources() != null
                    && e.sources().size() == 2
                    && "文章A".equals(e.sources().get(0).getTitle())
                    && 0.9f == e.sources().get(0).getScore())
                .expectNextCount(2)  // content + done
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("限流检查")
    class RateLimiting {

        @Test
        @DisplayName("限流拒绝时应转换为 error 事件")
        void rateLimitRejectionShouldBecomeErrorEvent() {
            doThrow(new ChatRateLimiter.RateLimitExceededException("超频了"))
                .when(rateLimiter).check(anyString(), anyString());

            Flux<ChatEvent> events = service.chat("q", null, "v", "127.0.0.1");

            StepVerifier.create(events)
                .expectNextMatches(e -> "error".equals(e.type())
                    && e.message().contains("超频了"))
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("异常降级")
    class ErrorHandling {

        @Test
        @DisplayName("LLM 流失败 → error 事件（不裸断开）")
        void llmFailureShouldBecomeErrorEvent() {
            ConversationDO conv = new ConversationDO();
            conv.setId(1L);

            when(conversationManager.getOrCreateConversation(any(), any(), any())).thenReturn(conv);
            when(conversationManager.loadHistory(anyLong())).thenReturn(List.of());
            when(retrievalEngine.retrieve(anyString(), anyInt())).thenReturn(List.of());
            when(routingLLMService.chatStream(any()))
                .thenReturn(Flux.error(new RuntimeException("API 挂了")));

            Flux<ChatEvent> events = service.chat("q", 1L, "v", "127.0.0.1");

            StepVerifier.create(events)
                .expectNextMatches(e -> "meta".equals(e.type()))
                .expectNextMatches(e -> "sources".equals(e.type()))
                .expectNextMatches(e -> "error".equals(e.type())
                    && e.message().contains("暂时不可用"))
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("对话记忆")
    class ConversationMemory {

        @Test
        @DisplayName("已有对话 → 加载历史并传入 Prompt")
        void existingConversationShouldLoadHistory() {
            ConversationDO conv = new ConversationDO();
            conv.setId(5L);

            List<ChatMessage> history = List.of(
                ChatMessage.user("之前的问题"),
                ChatMessage.assistant("之前的回答")
            );

            when(conversationManager.getOrCreateConversation(5L, "v", "新问题"))
                .thenReturn(conv);
            when(conversationManager.loadHistory(5L)).thenReturn(history);
            when(retrievalEngine.retrieve("新问题", 5)).thenReturn(List.of());
            when(routingLLMService.chatStream(any())).thenReturn(Flux.just("回复"));

            Flux<ChatEvent> events = service.chat("新问题", 5L, "v", "127.0.0.1");

            StepVerifier.create(events)
                .expectNextCount(4)  // meta + sources + content + done
                .verifyComplete();

            verify(conversationManager).loadHistory(5L);
        }
    }
}
