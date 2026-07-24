package io.github.somehow.mysite.ragent.core.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.dao.entity.IntentDO;
import io.github.somehow.mysite.ragent.dao.mapper.IntentMapper;
import io.github.somehow.mysite.ragent.llm.LLMService;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图分类器 —— Ragent 的 intent 包精简版。
 *
 * <h3>与 Ragent 的对比</h3>
 * <ul>
 *   <li>Ragent: 树形多级（DOMAIN→CATEGORY→TOPIC），Redis 缓存意图树，
 *       LLM 对所有叶子节点打分，score&lt;0.35 过滤，每子问题最多取 3 个意图，
 *       歧义时生成引导选项</li>
 *   <li>MySite: 扁平列表，LLM 从 N 个意图中选最匹配的 1 个，低置信度时降级到全局检索，
 *       不做树形编辑 UI（博客 3-5 个 KB，树形过度设计）</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ol>
 *   <li>用 LLM 做分类而不是关键词匹配 —— "怎么看 Spring 源码？"
 *       "源码"不在 keywords 里，但 LLM 知道这属于技术问题而非读书推荐</li>
 *   <li>分类 LLM 调用用 cheap model（如 deepseek-chat），
 *       不占用聊天生成用的主力模型额度</li>
 *   <li>分类失败时优雅降级为 fallback（全局检索），不阻塞管道</li>
 * </ol>
 */
@Slf4j
@Component
public class IntentClassifier {

    private final IntentMapper intentMapper;
    private final LLMService classificationLLM;
    private final ObjectMapper objectMapper;

    public IntentClassifier(IntentMapper intentMapper,
                            @Qualifier("classificationLLM") LLMService classificationLLM,
                            ObjectMapper objectMapper) {
        this.intentMapper = intentMapper;
        this.classificationLLM = classificationLLM;
        this.objectMapper = objectMapper;
    }

    /**
     * 对用户问题做意图分类。
     *
     * @param question 用户当前问题（可能是改写后的主查询）
     * @param history  最近几轮对话（用于指代消解后的上下文中判断意图）
     * @return 分类结果，包含目标 KB、置信度、是否需要引导
     */
    public IntentResult classify(String question, List<ChatMessage> history) {
        // 1. 加载所有已启用的意图（博客规模，全量加载）
        List<IntentDO> intents = intentMapper.listEnabled();

        if (intents.isEmpty()) {
            log.debug("[intent] no intents configured, using fallback");
            return IntentResult.fallback();
        }

        // 2. 构造分类 Prompt
        String classificationPrompt = buildClassificationPrompt(intents, question, history);

        // 3. 调用轻量 LLM 做分类（非流式，fast path）
        try {
            String llmOutput = classificationLLM.chat(
                ChatRequest.of("deepseek-chat", classificationPrompt));

            // 4. 解析 LLM 输出
            return parseIntentResult(llmOutput, intents);
        } catch (Exception e) {
            log.warn("[intent] classification LLM call failed: {}", e.getMessage());
            return IntentResult.fallback();
        }
    }

    /**
     * 构造分类 Prompt —— 告诉 LLM 有哪些意图可选、每个意图代表什么，
     * 让它输出 JSON 格式的分类结果。
     */
    String buildClassificationPrompt(List<IntentDO> intents, String question,
                                     List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            你是一个意图分类器。根据用户问题判断它属于以下哪个意图。

            输出格式（严格 JSON，不要 markdown code block）：
            {"intentId": <数字>, "confidence": <0.0-1.0>, "reason": "<一句话理由>",
             "needsGuidance": <true|false>}

            needsGuidance = true 的情况（必须判断）：
            - 问题过于模糊/简短，无法确定用户真正想问什么（例："Spring 怎么样？"）
            - 问题有歧义，可能匹配多个意图且置信度接近（例："推荐一本好书"——技术书还是小说？）
            - 问题含代词但缺少上下文（例："那个怎么搞？"，且历史对话为空或未涉及该代词）

            ## 候选意图列表
            """);

        for (IntentDO intent : intents) {
            sb.append("- ID=%d | 类型=%s | 名称=%s | 描述=%s\n".formatted(
                intent.getId(), intent.getType(), intent.getName(), intent.getDescription()));
        }

        // 附加最近 2 轮对话帮助 LLM 做指代消解后的意图判断
        if (history != null && !history.isEmpty()) {
            sb.append("\n## 对话历史（最近 2 轮）\n");
            int start = Math.max(0, history.size() - 4);  // 2 轮 = 4 条
            for (int i = start; i < history.size(); i++) {
                ChatMessage m = history.get(i);
                sb.append("- [%s]: %s\n".formatted(m.getRole(), m.getContent()));
            }
        }

        sb.append("\n## 用户当前问题\n").append(question).append("\n\n");
        sb.append("请输出 JSON（不要 markdown code block，直接输出 JSON）：");
        return sb.toString();
    }

    /**
     * 解析 LLM 输出的 JSON 分类结果。
     * <p>
     * 容错设计：LLM 可能输出格式错误、intentId 不存在等，
     * 任何解析失败都降级为 fallback（全局检索），不阻塞管道。
     */
    IntentResult parseIntentResult(String llmOutput, List<IntentDO> intents) {
        try {
            // 清理 LLM 可能包裹的 ```json 标记
            String json = llmOutput.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            JsonNode root = objectMapper.readTree(json);
            long intentId = root.get("intentId").asLong();
            double confidence = clamp(root.get("confidence").asDouble(), 0.0, 1.0);
            String reason = root.path("reason").asText("");
            boolean needsGuidance = root.path("needsGuidance").asBoolean(false);

            IntentDO matched = intents.stream()
                .filter(i -> i.getId() == intentId)
                .findFirst()
                .orElse(null);

            if (matched == null) {
                log.warn("[intent] LLM returned unknown intentId={}, falling back to global", intentId);
                return IntentResult.fallback();
            }

            log.info("[intent] classified as '{}' (type={}, kbId={}, confidence={:.2f})",
                matched.getName(), matched.getType(), matched.getKbId(), confidence);

            return IntentResult.builder()
                .intentId(intentId)
                .type(matched.getType())
                .targetKbId(matched.getKbId())
                .confidence(confidence)
                .needsGuidance(needsGuidance)
                .reason(reason)
                .customPromptFragment(matched.getCustomPromptFragment())
                .customTopK(matched.getCustomTopK())
                .build();

        } catch (Exception e) {
            log.warn("[intent] failed to parse classification result: {}", e.getMessage());
            return IntentResult.fallback();
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
