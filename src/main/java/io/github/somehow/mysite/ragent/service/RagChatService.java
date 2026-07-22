package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.core.PromptTemplate;
import io.github.somehow.mysite.ragent.core.memory.ConversationManager;
import io.github.somehow.mysite.ragent.core.retrieval.RetrievalEngine;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.ChatEvent;
import io.github.somehow.mysite.ragent.llm.ChatMessage;
import io.github.somehow.mysite.ragent.llm.ChatRequest;
import io.github.somehow.mysite.ragent.llm.RoutingLLMService;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * RAG 问答核心服务 —— 整个 RAG 系统的"大脑皮层"。
 *
 * 完整链路：
 *   0. 成本保护（限流 + 问题长度）
 *   1. 获取或创建会话（visitorId 归属 + IDOR 防护）
 *   2. 加载对话记忆（滑动窗口，默认 6 轮）
 *   3. 向量检索 + Rerank 精排（两阶段检索）
 *   4. 组装 Prompt（检索上下文 + 对话历史 + 用户问题）
 *   5. LLM 流式生成（多供应商路由 + 断路器自动降级）
 *   6. 完成后保存问答记录（阻塞 JDBC → boundedElastic）
 *
 * SSE 事件序列：
 *   meta    ×1 → 新会话回传 conversationId，前端存下来用于后续轮次
 *   sources ×1 → 检索到的引用来源，排在首条回答之前
 *   content ×N → LLM 输出的每个 token
 *   done    ×1 → 正常结束
 *   error   ×1 → 异常中断（不裸断开连接，前端可展示提示）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final RetrievalEngine retrievalEngine;
    private final ConversationManager conversationManager;
    private final PromptTemplate promptTemplate;
    private final RoutingLLMService routingLLMService;
    private final ChatRateLimiter rateLimiter;
    private final RagProperties properties;

    /**
     * RAG 流式问答 —— 核心入口。
     *
     * @param question       用户问题
     * @param conversationId 对话 ID（null = 新对话）
     * @param visitorId      匿名访客标识（前端 localStorage UUID）
     * @param clientIp       客户端 IP（限流用，开发环境可为 null）
     * @return ChatEvent 事件流（meta → sources → content×N → done）
     */
    public Flux<ChatEvent> chat(String question, Long conversationId,
                                String visitorId, String clientIp) {
        // Step 0: 成本保护（限流检查是同步的，切到 boundedElastic 执行）
        return Mono.fromCallable(() -> {
                rateLimiter.check(clientIp, question);
                return true;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(ok -> doChat(question, conversationId, visitorId))
            .onErrorResume(e -> {
                if (e instanceof ChatRateLimiter.RateLimitExceededException) {
                    return Flux.just(ChatEvent.error(e.getMessage()));
                }
                return Flux.error(e);
            });
    }

    private Flux<ChatEvent> doChat(String question, Long conversationId, String visitorId) {
        // Step 1: 获取或创建会话
        ConversationDO conversation = conversationManager
            .getOrCreateConversation(conversationId, visitorId, question);
        Long convId = conversation.getId();

        // Step 2: 加载对话历史
        List<ChatMessage> history = conversationManager.loadHistory(convId);

        // Step 3: 向量检索 + Rerank
        List<SearchResult> retrieved = retrievalEngine.retrieve(
            question, properties.getRetrieval().getRerankTopK());
        List<SourceChunkDTO> sources = retrieved.stream()
            .map(r -> new SourceChunkDTO(r.docTitle(), r.content(), r.score()))
            .toList();

        // Step 4: 组装 Prompt（有结果 → RAG，无结果 → 通用聊天）
        List<ChatMessage> messages = retrieved.isEmpty()
            ? promptTemplate.buildGeneralPrompt(question, history)
            : promptTemplate.buildRagPrompt(question, retrieved, history);

        ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .temperature(0.7)
            .maxTokens(2048)
            .build();

        // Step 5 & 6: LLM 流式生成 → 事件流 → 完成后落库
        StringBuilder fullAnswer = new StringBuilder();

        return Flux.concat(
                // meta（会话 ID）+ sources（引用来源）
                Flux.just(ChatEvent.meta(convId), ChatEvent.sources(sources)),
                // LLM token 流 → content 事件
                routingLLMService.chatStream(request)
                    .map(ChatEvent::content)
                    .doOnNext(e -> {
                        if ("content".equals(e.type())) {
                            fullAnswer.append(e.delta());
                        }
                    }),
                // done 事件
                Flux.just(ChatEvent.done())
            )
            // 落库是阻塞 JDBC，切到 boundedElastic 不占 event-loop
            .publishOn(Schedulers.boundedElastic())
            .doOnComplete(() -> {
                try {
                    conversationManager.saveExchange(
                        convId, question, fullAnswer.toString(), sources);
                } catch (Exception e) {
                    log.error("Failed to save exchange for conversation {}", convId, e);
                }
            })
            // 统一兜底：任何异常转为 error 事件（不裸断开）
            .onErrorResume(e -> {
                log.error("RAG chat pipeline error", e);
                String msg = e instanceof ChatRateLimiter.RateLimitExceededException
                    ? e.getMessage()
                    : "AI 服务暂时不可用，请稍后再试";
                return Flux.just(ChatEvent.error(msg));
            });
    }
}
