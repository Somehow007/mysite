package io.github.somehow.mysite.ragent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.ragent.core.ConversationManager;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMapper;
import io.github.somehow.mysite.ragent.llm.model.ChatEvent;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.service.ChatRateLimiter;
import io.github.somehow.mysite.ragent.service.RagChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;

import reactor.core.Disposable;

/**
 * RAG 聊天 SSE 端点。
 *
 * SSE (Server-Sent Events) 协议：
 *   请求：GET /v1/rag/chat/stream?q=xxx&conversationId=123&visitorId=uuid
 *   响应：Content-Type: text/event-stream
 *   事件格式（每行一个 JSON）：
 *     data: {"type":"meta","conversationId":123}\n\n
 *     data: {"type":"sources","sources":[...]}\n\n
 *     data: {"type":"content","delta":"你好"}\n\n
 *     data: {"type":"done"}\n\n
 *     data: {"type":"error","message":"..."}\n\n
 */
@Slf4j
@RestController
@RequestMapping("/v1/rag")
public class RagChatController {

    private final RagChatService ragChatService;
    private final Executor ragExecutor;
    private final ObjectMapper objectMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationManager conversationManager;

    public RagChatController(RagChatService ragChatService,
                             @Qualifier("ragAsyncExecutor") Executor ragExecutor,
                             ObjectMapper objectMapper,
                             ConversationMapper conversationMapper,
                             ConversationManager conversationManager) {
        this.ragChatService = ragChatService;
        this.ragExecutor = ragExecutor;
        this.objectMapper = objectMapper;
        this.conversationMapper = conversationMapper;
        this.conversationManager = conversationManager;
    }

    /**
     * SSE 流式聊天端点。
     *
     * @param question       用户问题
     * @param conversationId 对话 ID（null = 新对话，后端自动创建并回传）
     * @param visitorId      匿名访客标识（前端 localStorage UUID，归属会话）
     * @param request        HTTP 请求（取客户端 IP 做限流）
     * @return SseEmitter 长连接，逐事件推送 JSON
     */
    @GetMapping("/chat/stream")
    public SseEmitter chatStream(
            @RequestParam("q") String question,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            @RequestParam(value = "visitorId", required = false) String visitorId,
            HttpServletRequest request) {

        SseEmitter emitter = new SseEmitter(120_000L);  // 120 秒超时
        String clientIp = resolveClientIp(request);
        UserRole userRole = UserContext.getRole();
        log.info("RAG chat request: ip={}, role={}, visitorId={}, convId={}, qLen={}",
            clientIp, userRole, visitorId, conversationId, question.length());

        // ── 优雅终止：存储订阅句柄，客户端断开 / 超时时取消后端 Flux ──
        final Disposable[] subscriptionHolder = new Disposable[1];
        Runnable cleanup = () -> {
            Disposable d = subscriptionHolder[0];
            if (d != null && !d.isDisposed()) {
                d.dispose();
                log.debug("SSE subscription disposed: clientIp={}", clientIp);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        ragExecutor.execute(() -> {
            try {
                Disposable subscription = ragChatService
                    .chat(question, conversationId, visitorId, clientIp, userRole)
                    .subscribe(
                        event -> sendEvent(emitter, event),
                        // service 层已兜底为 error 事件，理论上这里走不到
                        error -> {
                            log.error("RAG chat subscription error", error);
                            sendEvent(emitter, ChatEvent.error("AI 服务异常"));
                        },
                        // 正常结束时 done/error 事件内已调用 emitter.complete()
                        () -> log.debug("RAG chat stream completed normally")
                    );
                subscriptionHolder[0] = subscription;
            } catch (ChatRateLimiter.RateLimitExceededException e) {
                // 同步阶段限流拒绝（subscribe 之前）
                sendEvent(emitter, ChatEvent.error(e.getMessage()));
            } catch (Exception e) {
                log.error("RAG chat stream setup failed", e);
                sendEvent(emitter, ChatEvent.error("AI 服务暂时不可用，请稍后再试"));
            }
        });

        return emitter;
    }

    /**
     * 获取对话历史列表（会话摘要）。
     * 用于前端展示历史对话侧栏，用户可切换或新建对话。
     */
    @GetMapping("/conversations")
    public List<Map<String, Object>> listConversations(
            @RequestParam(value = "visitorId", required = false) String visitorId,
            @RequestParam(value = "userId", required = false) Long userId) {

        List<ConversationDO> conversations;
        if (userId != null) {
            conversations = conversationMapper.selectByUserId(userId);
        } else if (visitorId != null && !visitorId.isBlank()) {
            conversations = conversationMapper.selectByVisitorId(visitorId);
        } else {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ConversationDO conv : conversations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(conv.getId()));
            item.put("title", conv.getTitle());
            item.put("messageCount", conv.getMessageCount());
            item.put("createTime", conv.getCreateTime());
            item.put("updateTime", conv.getUpdateTime());
            result.add(item);
        }
        return result;
    }

    /**
     * 获取指定会话的历史消息。
     * 前端切换/恢复历史对话时调用，将消息渲染到聊天面板中。
     */
    @GetMapping("/conversations/{id}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable Long id) {
        List<ChatMessage> messages = conversationManager.loadHistory(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", msg.getRole());           // "USER" | "ASSISTANT"
            item.put("content", msg.getContent());
            result.add(item);
        }
        return result;
    }

    /**
     * 序列化 ChatEvent 并推送到 SSE 连接。
     * done/error 事件推送后关闭连接；客户端断连时静默结束（IOException 是正常行为）。
     *
     * 注意：data() 不传 MediaType.APPLICATION_JSON，因为 writeValueAsString 已经产出了
     * JSON 字符串；再传 MediaType 会让 Spring 的 MappingJackson2HttpMessageConverter
     * 对字符串再序列化一次（加 JSON 字符串引号），导致前端 JSON.parse 得到的是字符串而非
     * 对象 —— 所有事件的 data.type 为 undefined，消息完全不显示。
     */
    private void sendEvent(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .data(objectMapper.writeValueAsString(event)));
            if ("done".equals(event.type()) || "error".equals(event.type())) {
                emitter.complete();
            }
        } catch (IOException e) {
            // 客户端主动断开是正常断连，不算异常
            emitter.completeWithError(e);
        }
    }

    /**
     * 取真实客户端 IP：Nginx 反代后 X-Forwarded-For 携带原始 IP。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
