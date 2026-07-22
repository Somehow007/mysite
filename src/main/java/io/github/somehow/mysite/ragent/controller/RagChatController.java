package io.github.somehow.mysite.ragent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.ChatEvent;
import io.github.somehow.mysite.ragent.service.ChatRateLimiter;
import io.github.somehow.mysite.ragent.service.RagChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;

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
@RestController
@RequestMapping("/v1/rag")
public class RagChatController {

    private final RagChatService ragChatService;
    private final Executor ragExecutor;
    private final ObjectMapper objectMapper;

    public RagChatController(RagChatService ragChatService,
                             @Qualifier("ragAsyncExecutor") Executor ragExecutor,
                             ObjectMapper objectMapper) {
        this.ragChatService = ragChatService;
        this.ragExecutor = ragExecutor;
        this.objectMapper = objectMapper;
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

        ragExecutor.execute(() -> {
            try {
                ragChatService.chat(question, conversationId, visitorId, clientIp)
                    .subscribe(
                        event -> sendEvent(emitter, event),
                        // service 层已兜底为 error 事件，理论上这里走不到
                        error -> {
                            sendEvent(emitter, ChatEvent.error("AI 服务异常"));
                        },
                        // 正常结束时 done/error 事件内已调用 emitter.complete()
                        () -> {}
                    );
            } catch (ChatRateLimiter.RateLimitExceededException e) {
                // 同步阶段限流拒绝（subscribe 之前）
                sendEvent(emitter, ChatEvent.error(e.getMessage()));
            } catch (Exception e) {
                sendEvent(emitter, ChatEvent.error("AI 服务暂时不可用，请稍后再试"));
            }
        });

        return emitter;
    }

    /**
     * 序列化 ChatEvent 并推送到 SSE 连接。
     * done/error 事件推送后关闭连接；客户端断连时静默结束（IOException 是正常行为）。
     */
    private void sendEvent(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .data(objectMapper.writeValueAsString(event), MediaType.APPLICATION_JSON));
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
