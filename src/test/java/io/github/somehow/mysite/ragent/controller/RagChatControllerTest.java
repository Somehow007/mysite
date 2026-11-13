package io.github.somehow.mysite.ragent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.context.UserInfoDTO;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.ragent.core.ConversationManager;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMapper;
import io.github.somehow.mysite.ragent.llm.model.ChatEvent;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.service.ChatRateLimiter;
import io.github.somehow.mysite.ragent.service.RagChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RagChatController SSE 端点 characterization tests。
 *
 * <p>钉住线上协议契约：事件顺序与 JSON 线格式、done/error 后关闭连接、
 * XFF 取 IP、kbIds 解析、同步异常降级为 error 事件。
 * 不依赖 Spring MVC——手动 initialize SseEmitter 的 Handler 并同步执行
 * 捕获的 executor 任务，行为与异步线程池等价。</p>
 */
@ExtendWith(MockitoExtension.class)
class RagChatControllerTest {

    /** ResponseBodyEmitter.Handler 是包级私有接口，用反射 + 动态代理注入。 */
    private static final Class<?> HANDLER_CLASS;
    static {
        try {
            HANDLER_CLASS = Class.forName(
                    "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Mock private RagChatService ragChatService;
    @Mock private ConversationMapper conversationMapper;
    @Mock private ConversationManager conversationManager;
    @Mock private HttpServletRequest request;

    private RagChatController controller;
    private final AtomicReference<Runnable> capturedTask = new AtomicReference<>();
    private final List<String> sentPayloads = new ArrayList<>();
    private volatile boolean completed;

    @BeforeEach
    void setUp() {
        // 同步捕获 executor：chatStream() 返回后手动 run，保证事件在断言前全部发出
        Executor capturingExecutor = capturedTask::set;
        controller = new RagChatController(
                ragChatService, capturingExecutor, new ObjectMapper(),
                conversationMapper, conversationManager);
        sentPayloads.clear();
        completed = false;
        capturedTask.set(null);
        // lenient：会话历史端点测试不经过 chatStream，不会用到 request 桩
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /** 记录型 SseEmitter Handler（动态代理）：收集每个 data 负载与 complete 信号。 */
    private void initializeEmitter(SseEmitter emitter) throws Exception {
        Object handler = Proxy.newProxyInstance(
                RagChatControllerTest.class.getClassLoader(),
                new Class<?>[]{HANDLER_CLASS},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "send" -> {
                            Object payload = args[0];
                            if (payload instanceof Set<?> items) {
                                // 一个 SSE 事件被拆成多个 DataWithMediaType，合并为一条逻辑事件
                                StringBuilder sb = new StringBuilder();
                                for (Object item : items) {
                                    sb.append(((ResponseBodyEmitter.DataWithMediaType) item).getData());
                                }
                                sentPayloads.add(sb.toString());
                            } else {
                                sentPayloads.add(String.valueOf(payload));
                            }
                        }
                        case "complete", "completeWithError" -> completed = true;
                        case "toString" -> { return "recording-handler"; }
                        case "hashCode" -> { return System.identityHashCode(proxy); }
                        case "equals" -> { return proxy == args[0]; }
                    }
                    return null;
                });
        Method init = ResponseBodyEmitter.class.getDeclaredMethod("initialize", HANDLER_CLASS);
        init.setAccessible(true);
        init.invoke(emitter, handler);
    }

    private SseEmitter startStream(String q, Long convId, String visitorId,
                                   List<String> kbIds, Long articleId) throws Exception {
        SseEmitter emitter = controller.chatStream(q, convId, visitorId, kbIds, articleId, request);
        initializeEmitter(emitter);
        assertNotNull(capturedTask.get(), "chatStream 应向 executor 提交任务");
        capturedTask.get().run();
        return emitter;
    }

    // ─────────────────────────── SSE 主流程 ───────────────────────────

    @Test
    void chatStream_shouldEmitMetaContentDoneInOrder() throws Exception {
        when(ragChatService.chat(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(
                        ChatEvent.meta(123L),
                        ChatEvent.content("你"),
                        ChatEvent.content("好"),
                        ChatEvent.done()));

        startStream("你好", null, "visitor-1", null, null);

        assertEquals(4, sentPayloads.size());
        assertTrue(sentPayloads.get(0).contains("\"type\":\"meta\""), sentPayloads.get(0));
        assertTrue(sentPayloads.get(0).contains("\"conversationId\":\"123\""), "Snowflake ID 应序列化为 string");
        assertTrue(sentPayloads.get(1).contains("\"type\":\"content\"") && sentPayloads.get(1).contains("\"delta\":\"你\""));
        assertTrue(sentPayloads.get(2).contains("\"type\":\"content\"") && sentPayloads.get(2).contains("\"delta\":\"好\""));
        assertTrue(sentPayloads.get(3).contains("\"type\":\"done\""));
        assertTrue(completed, "done 事件后应关闭连接");

        verify(ragChatService).chat(eq("你好"), isNull(), eq("visitor-1"),
                eq("127.0.0.1"), isNull(), eq(List.of()), isNull(), isNull(), isNull());
    }

    @Test
    void chatStream_xffHeader_shouldUseFirstIpForRateLimit() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        when(ragChatService.chat(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(ChatEvent.done()));

        startStream("hi", null, null, null, null);

        verify(ragChatService).chat(any(), any(), any(), eq("1.2.3.4"),
                any(), any(), any(), any(), any());
    }

    @Test
    void chatStream_kbIds_shouldParseStringsToLongs() throws Exception {
        when(ragChatService.chat(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(ChatEvent.done()));

        startStream("q", null, null, List.of("11", "22"), 99L);

        verify(ragChatService).chat(any(), any(), any(), any(), any(),
                eq(List.of(11L, 22L)), any(), any(), eq(99L));
    }

    @Test
    void chatStream_invalidKbId_shouldThrowNumberFormatException() {
        // 现状钉住：kbIds 在进入 SSE 流之前同步 Long.parseLong，非法值抛
        // NumberFormatException → Spring 返回 500，而非 SSE error 事件。
        // 前端 kbIds 由本系统生成，正常不会触发；记录为已知边界行为。
        assertThrows(NumberFormatException.class, () ->
                controller.chatStream("q", null, null, List.of("abc"), null, request));
        verifyNoInteractions(ragChatService);
    }

    @Test
    void chatStream_rateLimitSyncThrow_shouldEmitErrorEvent() throws Exception {
        when(ragChatService.chat(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ChatRateLimiter.RateLimitExceededException("每小时提问次数已达上限"));

        startStream("q", null, null, null, null);

        assertEquals(1, sentPayloads.size());
        assertTrue(sentPayloads.get(0).contains("\"type\":\"error\""), sentPayloads.get(0));
        assertTrue(sentPayloads.get(0).contains("每小时提问次数已达上限"));
        assertTrue(completed);
    }

    @Test
    void chatStream_serviceSetupFailure_shouldEmitGenericErrorEvent() throws Exception {
        when(ragChatService.chat(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        startStream("q", null, null, null, null);

        assertEquals(1, sentPayloads.size());
        assertTrue(sentPayloads.get(0).contains("\"type\":\"error\""));
        assertTrue(sentPayloads.get(0).contains("AI 服务暂时不可用"));
        assertTrue(completed);
    }

    @Test
    void chatStream_loggedInUser_shouldPassUserIdAndRole() throws Exception {
        UserContext.setUser(UserInfoDTO.builder()
                .userId("42").name("admin").role(UserRole.ADMIN).build());
        when(ragChatService.chat(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(ChatEvent.done()));

        startStream("q", null, null, null, null);

        verify(ragChatService).chat(any(), any(), any(), any(), eq(UserRole.ADMIN),
                any(), eq(42L), eq("admin"), any());
    }

    // ─────────────────────── 会话历史端点 ───────────────────────

    @Test
    void listConversations_userId_shouldQueryByUserId() {
        ConversationDO conv = new ConversationDO();
        conv.setId(7L);
        conv.setTitle("标题");
        conv.setMessageCount(3);
        conv.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        conv.setUpdateTime(LocalDateTime.of(2026, 1, 2, 10, 0));
        when(conversationMapper.selectByUserId(42L)).thenReturn(List.of(conv));

        List<?> result = controller.listConversations(null, 42L);

        assertEquals(1, result.size());
        @SuppressWarnings("unchecked")
        var item = (java.util.Map<String, Object>) result.get(0);
        assertEquals("7", item.get("id"));   // Snowflake ID 序列化为 string
        assertEquals("标题", item.get("title"));
        assertEquals(3, item.get("messageCount"));
        verify(conversationMapper, never()).selectByVisitorId(any());
    }

    @Test
    void listConversations_visitorId_shouldQueryByVisitor() {
        when(conversationMapper.selectByVisitorId("v-1")).thenReturn(List.of());

        List<?> result = controller.listConversations("v-1", null);

        assertTrue(result.isEmpty());
        verify(conversationMapper).selectByVisitorId("v-1");
        verify(conversationMapper, never()).selectByUserId(anyLong());
    }

    @Test
    void listConversations_neitherParam_shouldReturnEmpty() {
        List<?> result = controller.listConversations(null, null);
        assertTrue(result.isEmpty());
        verifyNoInteractions(conversationMapper);
    }

    @Test
    void getMessages_shouldMapRoleAndContent() {
        when(conversationManager.loadHistory(9L)).thenReturn(List.of(
                ChatMessage.user("问题"),
                ChatMessage.assistant("回答")));

        List<?> result = controller.getMessages(9L);

        assertEquals(2, result.size());
        @SuppressWarnings("unchecked")
        var first = (java.util.Map<String, Object>) result.get(0);
        assertEquals("user", first.get("role"));
        assertEquals("问题", first.get("content"));
        @SuppressWarnings("unchecked")
        var second = (java.util.Map<String, Object>) result.get(1);
        assertEquals("assistant", second.get("role"));
    }
}
