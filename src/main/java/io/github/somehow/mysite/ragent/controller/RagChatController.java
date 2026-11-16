package io.github.somehow.mysite.ragent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.ragent.core.ConversationManager;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.mapper.ConversationMapper;
import io.github.somehow.mysite.ragent.llm.model.ChatEvent;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.service.ChatRateLimiter;
import io.github.somehow.mysite.ragent.service.RagChatService;
import io.github.somehow.mysite.utils.ClientIpResolver;
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
 *   请求：GET /v1/rag/chat/stream?q=xxx&conversationId=123&visitorId=uuid（需登录）
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
     * @param visitorId      会话归属标识（前端 localStorage UUID，登录用户同样携带）
     * @param request        HTTP 请求（取客户端 IP 做限流）
     * @return SseEmitter 长连接，逐事件推送 JSON
     */
    @GetMapping("/chat/stream")
    public SseEmitter chatStream(
            @RequestParam("q") String question,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            @RequestParam(value = "visitorId", required = false) String visitorId,
            @RequestParam(value = "kbIds", required = false) List<String> kbIdStrings,
            @RequestParam(value = "articleId", required = false) Long articleId,
            HttpServletRequest request) {

        SseEmitter emitter = new SseEmitter(120_000L);  // 120 秒超时
        String clientIp = ClientIpResolver.resolve(request);
        UserRole userRole = UserContext.getRole();
        Long userId = parseUserId(UserContext.getUserId());
        String username = UserContext.getName();

        // 将前端传来的 string kbIds 转为 Long（避免 Spring 自动转换的潜在问题）
        List<Long> kbIds = (kbIdStrings != null && !kbIdStrings.isEmpty())
            ? kbIdStrings.stream().map(Long::parseLong).toList()
            : List.of();

        log.info("RAG chat request: ip={}, role={}, visitorId={}, convId={}, qLen={}, kbIds={}, articleId={}",
            clientIp, userRole, maskVisitorId(visitorId), conversationId, question.length(), kbIds, articleId);

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
                    .chat(question, conversationId, visitorId, clientIp, userRole, kbIds, userId, username, articleId)
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
     * 归属：user_id 主 key（登录账号），visitorId 仅兜底未认领存量会话——
     * user_id 不接受请求参数（可伪造），一律取自登录态 UserContext。
     */
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> listConversations(
            @RequestParam(value = "visitorId", required = false) String visitorId) {

        Long userId = parseUserId(UserContext.getUserId());
        List<ConversationDO> conversations =
                conversationMapper.selectVisibleTo(userId, visitorId);

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
        return Results.success(result);
    }

    /**
     * 获取指定会话的历史消息。
     * 前端切换/恢复历史对话时调用，将消息渲染到聊天面板中。
     * 归属校验：必须命中 user_id 主归属或 visitor_id 兜底，否则返回空
     * （不泄露会话是否存在，与 listConversations 空语义一致）。
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<Map<String, Object>>> getMessages(
            @PathVariable Long id,
            @RequestParam(value = "visitorId", required = false) String visitorId) {
        ConversationDO conv = conversationMapper.selectById(id);
        Long userId = parseUserId(UserContext.getUserId());
        if (conv == null || !ConversationManager.isOwner(conv, userId, visitorId)) {
            return Results.success(List.of());
        }
        List<ChatMessage> messages = conversationManager.loadHistory(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", msg.getRole());           // "USER" | "ASSISTANT"
            item.put("content", msg.getContent());
            result.add(item);
        }
        return Results.success(result);
    }

    /**
     * 重命名会话。归属校验失败与会话不存在统一报「会话不存在」，
     * 不区分两种情形（与读路径不泄露存在性的口径一致）。
     */
    @PutMapping("/conversations/{id}/rename")
    public Result<Void> renameConversation(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestParam(value = "visitorId", required = false) String visitorId) {
        Long userId = parseUserId(UserContext.getUserId());
        conversationManager.renameConversation(id, userId, visitorId,
                body == null ? null : body.get("title"));
        return Results.success();
    }

    /**
     * 删除会话及其全部消息（同事务）。归属口径同 rename。
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @PathVariable Long id,
            @RequestParam(value = "visitorId", required = false) String visitorId) {
        Long userId = parseUserId(UserContext.getUserId());
        conversationManager.deleteConversation(id, userId, visitorId);
        return Results.success();
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
     * visitorId 是会话归属凭证，日志只留前缀防泄露后被冒用拉取历史。
     */
    private static String maskVisitorId(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return visitorId;
        }
        return visitorId.length() <= 8 ? "***" : visitorId.substring(0, 8) + "***";
    }

    private static Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
