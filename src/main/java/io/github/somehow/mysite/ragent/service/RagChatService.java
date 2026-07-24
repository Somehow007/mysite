package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.core.ConversationManager;
import io.github.somehow.mysite.ragent.core.PromptTemplate;
import io.github.somehow.mysite.ragent.core.RetrievalEngine;
import io.github.somehow.mysite.ragent.core.intent.GuidanceHandler;
import io.github.somehow.mysite.ragent.core.intent.GuidanceHandler.IntentCandidate;
import io.github.somehow.mysite.ragent.core.intent.IntentClassifier;
import io.github.somehow.mysite.ragent.core.intent.IntentResult;
import io.github.somehow.mysite.ragent.core.rewrite.QueryRewriter;
import io.github.somehow.mysite.ragent.core.rewrite.QueryRewriter.RewriteResult;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.IntentDO;
import io.github.somehow.mysite.ragent.dao.mapper.IntentMapper;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;
import io.github.somehow.mysite.ragent.llm.RoutingLLMService;
import io.github.somehow.mysite.ragent.llm.model.ChatEvent;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * RAG 问答核心服务 —— 7 阶段智能管道（Phase 6 升级）。
 *
 * <h3>管道阶段</h3>
 * <pre>
 *   0. 成本保护（限流 + 问题长度）
 *   1. 加载对话记忆
 *   2. ★ 查询改写（指代消解 / 拆分 / 口语正规化）
 *   3. ★ 意图分类（LLM 分类器 → KB_RETRIEVAL / CHAT / 歧义）
 *   [短路] 歧义引导 —— 低置信度时生成候选方向让用户选
 *   [短路] 闲聊直回 —— CHAT 类型跳过检索，直接 LLM 回复
 *   4. ★ 意图感知检索（定向 KB / 多子问题并行检索）
 *   5. ★ 意图感知 Prompt（customPromptFragment + KB 名称标注）
 *   6. LLM 流式生成 → SSE 推送 → 落库
 * </pre>
 */
@Slf4j
@Service
public class RagChatService {

    private final RetrievalEngine retrievalEngine;
    private final ConversationManager conversationManager;
    private final PromptTemplate promptTemplate;
    private final RoutingLLMService routingLLMService;
    private final ChatRateLimiter rateLimiter;
    private final RagProperties properties;

    // ── Phase 6 新增 ──
    private final QueryRewriter queryRewriter;
    private final IntentClassifier intentClassifier;
    private final GuidanceHandler guidanceHandler;
    private final IntentMapper intentMapper;

    public RagChatService(RetrievalEngine retrievalEngine,
                          ConversationManager conversationManager,
                          PromptTemplate promptTemplate,
                          RoutingLLMService routingLLMService,
                          ChatRateLimiter rateLimiter,
                          RagProperties properties,
                          QueryRewriter queryRewriter,
                          IntentClassifier intentClassifier,
                          GuidanceHandler guidanceHandler,
                          IntentMapper intentMapper) {
        this.retrievalEngine = retrievalEngine;
        this.conversationManager = conversationManager;
        this.promptTemplate = promptTemplate;
        this.routingLLMService = routingLLMService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.queryRewriter = queryRewriter;
        this.intentClassifier = intentClassifier;
        this.guidanceHandler = guidanceHandler;
        this.intentMapper = intentMapper;
    }

    /**
     * RAG 流式问答 —— 核心入口。
     *
     * @param question       用户问题
     * @param conversationId 对话 ID（null = 新对话）
     * @param visitorId      匿名访客标识
     * @param clientIp       客户端 IP（限流用）
     * @param userRole       用户角色（限流阈值）
     * @param chosenIntentId 用户选择的意图 ID（guidance 回传，null = 自动分类）
     */
    public Flux<ChatEvent> chat(String question, Long conversationId,
                                String visitorId, String clientIp,
                                UserRole userRole, Long chosenIntentId) {
        // Step 0: 成本保护
        return Mono.fromCallable(() -> {
                rateLimiter.check(clientIp, question, userRole);
                return true;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(ok -> doChat(question, conversationId, visitorId, chosenIntentId))
            .onErrorResume(e -> {
                if (e instanceof ChatRateLimiter.RateLimitExceededException) {
                    return Flux.just(ChatEvent.error(e.getMessage()));
                }
                return Flux.error(e);
            });
    }

    // ── 7 阶段管道 ──

    private Flux<ChatEvent> doChat(String question, Long conversationId,
                                   String visitorId, Long chosenIntentId) {
        long t0 = System.currentTimeMillis();

        // Stage 1: 获取或创建会话 + 加载对话历史
        ConversationDO conversation = conversationManager
            .getOrCreateConversation(conversationId, visitorId, question);
        Long convId = conversation.getId();
        List<ChatMessage> history = conversationManager.loadHistory(convId);
        log.info("[pipeline] stage1: conv={}, history={}msgs ({}ms)",
            convId, history.size(), System.currentTimeMillis() - t0);

        // ── Stage 2: 查询改写 ──
        long t2 = System.currentTimeMillis();
        RewriteResult rewritten = queryRewriter.rewrite(question, history);
        String primaryQuery = rewritten.subQueries().get(0);
        log.info("[pipeline] stage2: rewritten={}, subQueries={} ({}ms)",
            rewritten.rewritten(), rewritten.subQueries().size(),
            System.currentTimeMillis() - t2);

        // ── Stage 3: 意图分类（或直接使用用户选择的意图）──
        long t3 = System.currentTimeMillis();
        IntentResult intent;
        if (chosenIntentId != null) {
            intent = resolveChosenIntent(chosenIntentId);
        } else {
            intent = intentClassifier.classify(primaryQuery, history);
        }
        log.info("[pipeline] stage3: type={}, targetKb={}, confidence={}, chosenIntentId={} ({}ms)",
            intent.getType(), intent.getTargetKbId(),
            String.format("%.2f", intent.getConfidence()),
            chosenIntentId, System.currentTimeMillis() - t3);

        // ── 短路 1: 歧义引导 ──
        if (chosenIntentId == null) {
            // 仅自动分类时检查歧义；用户已选意图则跳过
            List<IntentDO> allIntents = intentMapper.listEnabled();
            List<IntentCandidate> ranked = guidanceHandler.rankCandidates(allIntents);
            if (guidanceHandler.shouldGuide(intent, ranked)) {
                log.info("[pipeline] ambiguity detected → guidance event");
                return Flux.just(
                    ChatEvent.meta(convId),
                    guidanceHandler.buildGuidanceEvent(ranked),
                    ChatEvent.done()
                );
            }
        }

        // ── 短路 2: 闲聊直接回复 ──
        if (intent.isChat()) {
            log.info("[pipeline] chat-only intent → skipping retrieval");
            List<ChatMessage> messages = promptTemplate.buildGeneralPrompt(primaryQuery, history);
            return streamLLMResponse(messages, convId, question, List.of());
        }

        // ── Stage 4: 意图感知检索 ──
        long t4 = System.currentTimeMillis();
        int topK = intent.getCustomTopK() != null
            ? intent.getCustomTopK()
            : properties.getRetrieval().getRerankTopK();

        List<SearchResult> retrieved;
        if (rewritten.subQueries().size() > 1) {
            // 多子问题：分别检索 → 去重合并 → Rerank
            retrieved = retrievalEngine.multiRetrieve(
                rewritten.subQueries(), intent.getTargetKbId(), topK);
        } else {
            // 单问题（含原文未改写）：定向或全库检索
            retrieved = retrievalEngine.retrieve(primaryQuery, topK, intent.getTargetKbId());
        }
        log.info("[pipeline] stage4: {} results, targetKb={}, topK={} ({}ms)",
            retrieved.size(), intent.getTargetKbId(), topK,
            System.currentTimeMillis() - t4);

        List<SourceChunkDTO> sources = retrieved.stream()
            .map(r -> new SourceChunkDTO(r.docTitle(), r.content(), r.score(),
                r.kbId(), properties.getKbNameCache().getOrDefault(r.kbId(), "博客")))
            .toList();

        // ── Stage 5: 意图感知 Prompt ──
        List<ChatMessage> messages = promptTemplate.buildIntentAwarePrompt(
            primaryQuery, retrieved, history, intent);
        log.info("[pipeline] stage5: {} messages, {} chars",
            messages.size(), messages.stream().mapToInt(m -> m.getContent().length()).sum());

        // ── Stage 6: LLM 流式生成 → SSE 推送 → 落库 ──
        return streamLLMResponse(messages, convId, question, sources);
    }

    // ── 流式生成 + 落库（Stage 6）──

    private Flux<ChatEvent> streamLLMResponse(List<ChatMessage> messages, Long convId,
                                              String question,
                                              List<SourceChunkDTO> sources) {
        long t0 = System.currentTimeMillis();
        ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .temperature(0.7)
            .maxTokens(2048)
            .build();

        StringBuilder fullAnswer = new StringBuilder();
        final long[] firstTokenAt = { 0 };

        return Flux.concat(
                // meta + sources
                Flux.just(ChatEvent.meta(convId), ChatEvent.sources(sources)),
                // LLM token 流
                routingLLMService.chatStream(request)
                    .map(ChatEvent::content)
                    .doOnNext(e -> {
                        if ("content".equals(e.type())) {
                            if (firstTokenAt[0] == 0) {
                                firstTokenAt[0] = System.currentTimeMillis();
                                log.info("[pipeline] stage6: first token ({}ms since LLM call)",
                                    firstTokenAt[0] - t0);
                            }
                            fullAnswer.append(e.delta());
                        }
                    }),
                // 落库 + done 事件
                Mono.fromCallable(() -> {
                    try {
                        long t5 = System.currentTimeMillis();
                        conversationManager.saveExchange(
                            convId, question, fullAnswer.toString(), sources);
                        log.info("[pipeline] stage6: exchange saved ({}ms), total tokens={}, total={}ms",
                            System.currentTimeMillis() - t5, fullAnswer.length(),
                            System.currentTimeMillis() - t0);
                    } catch (Exception e) {
                        log.error("Failed to save exchange for conversation {}", convId, e);
                    }
                    return ChatEvent.done();
                }).subscribeOn(Schedulers.boundedElastic())
            )
            .onErrorResume(e -> {
                log.error("[pipeline] error", e);
                return Flux.just(ChatEvent.error("AI 服务暂时不可用，请稍后再试"));
            });
    }

    /**
     * 根据用户选择的 intentId 构造 IntentResult（跳过分类环节）。
     */
    private IntentResult resolveChosenIntent(Long intentId) {
        IntentDO intent = intentMapper.findById(intentId);
        if (intent == null) {
            log.warn("[pipeline] chosenIntentId={} not found, using fallback", intentId);
            return IntentResult.fallback();
        }
        log.info("[pipeline] using chosen intent: id={}, name={}, type={}",
            intent.getId(), intent.getName(), intent.getType());
        return IntentResult.builder()
            .intentId(intent.getId())
            .type(intent.getType())
            .targetKbId(intent.getKbId())
            .confidence(1.0)           // 用户手动选择 → 最高置信度
            .needsGuidance(false)
            .reason("user_chosen")
            .customPromptFragment(intent.getCustomPromptFragment())
            .customTopK(intent.getCustomTopK())
            .build();
    }
}
