package io.github.somehow.mysite.ragent.core.rewrite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMService;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询改写器 —— 让模糊的问题变清晰。
 *
 * <h3>什么时候需要改写？</h3>
 * <ol>
 *   <li><b>指代消解</b>："那个配置" + 历史"JWT 过滤器" → "JWT 过滤器要怎么配置？"
 *       —— 不做改写的话，"那个配置"的 embedding 向量毫无意义</li>
 *   <li><b>拆分长问题</b>：一个 200 字的问题含 3 个子问题 →
 *       拆成 3 个短问题分别检索，结果去重合并
 *       —— 长问题 embedding 语义被稀释，检索效果差</li>
 *   <li><b>口语化转正式</b>："搞个登录要咋整？" → "如何实现用户登录功能？"
 *       —— embedding 模型对口语化文本的向量质量不如正式文本</li>
 * </ol>
 *
 * <h3>什么时候不触发？（节省 LLM 调用成本）</h3>
 * <ul>
 *   <li>问题 &lt; 15 字且不含代词</li>
 *   <li>问题已经是清晰的技术问句（如 "JWT 认证怎么实现？"）</li>
 *   <li>闲聊类问题（"你好"、"谢谢"）—— 在 classifyIntent 阶段就会被短路</li>
 * </ul>
 */
@Slf4j
@Component
public class QueryRewriter {

    private final LLMService classificationLLM;
    private final ObjectMapper objectMapper;

    // ── 口语化检测词 ──
    private static final List<String> COLLOQUIAL_MARKERS = List.of("咋", "啥", "咋整", "咋搞", "搞一下");

    // ── 代词检测词 ──
    private static final List<String> PRONOUN_MARKERS = List.of("这个", "那个", "它", "上面", "前面", "刚才", "之前");

    public QueryRewriter(@Qualifier("classificationLLM") LLMService classificationLLM,
                         ObjectMapper objectMapper) {
        this.classificationLLM = classificationLLM;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询改写主入口。
     *
     * @param question 用户原始问题
     * @param history  对话历史（用于指代消解）
     * @return RewriteResult，含 1-N 个子问题
     */
    public RewriteResult rewrite(String question, List<ChatMessage> history) {
        // 快速判断：不需要改写的直接返回原文
        if (!needsRewrite(question, history)) {
            return RewriteResult.unchanged(question);
        }

        try {
            String prompt = buildRewritePrompt(question, history);
            String llmOutput = classificationLLM.chat(
                ChatRequest.of("deepseek-chat", prompt));
            return parseRewriteResult(llmOutput, question);
        } catch (Exception e) {
            log.warn("[rewrite] LLM call failed, using original query: {}", e.getMessage());
            return RewriteResult.unchanged(question);
        }
    }

    /**
     * 快速判断是否需要触发改写（纯规则，不调 LLM，零成本）。
     */
    boolean needsRewrite(String question, List<ChatMessage> history) {
        if (question == null || question.isEmpty()) return false;

        // 含代词 + 有历史 → 需要指代消解
        if (hasPronouns(question) && history != null && !history.isEmpty()) {
            return true;
        }
        // 过长（>80 字符）且含 "还有" / "另外" → 可能需要拆分
        if (question.length() > 80 && (question.contains("还有") || question.contains("另外"))) {
            return true;
        }
        // 过于口语化
        if (isColloquial(question)) {
            return true;
        }
        return false;
    }

    private boolean hasPronouns(String q) {
        return PRONOUN_MARKERS.stream().anyMatch(q::contains);
    }

    private boolean isColloquial(String q) {
        return COLLOQUIAL_MARKERS.stream().anyMatch(q::contains);
    }

    /**
     * 构造改写 Prompt。
     */
    String buildRewritePrompt(String question, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            将用户问题改写为适合向量检索的清晰查询。如果问题包含多个子问题，拆分为独立查询。

            输出格式（严格 JSON，不要 markdown code block）：
            {"subQueries": ["改写后的查询1", "查询2", ...]}

            规则：
            1. 代词（这个/那个/它）必须替换为历史对话中的具体内容
            2. 口语化表达（咋搞/啥意思）改写为正式技术用语
            3. 复合问题拆分为最多 3 个独立子问题
            4. 如果问题已经很清晰，subQueries 只包含一个元素（轻微优化措辞即可）

            """);

        if (history != null && !history.isEmpty()) {
            sb.append("## 对话历史\n");
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                ChatMessage m = history.get(i);
                sb.append("- [%s]: %s\n".formatted(m.getRole(), m.getContent()));
            }
            sb.append("\n");
        }

        sb.append("## 用户当前问题\n").append(question).append("\n\n");
        sb.append("## 改写结果（JSON）：");
        return sb.toString();
    }

    /**
     * 解析 LLM 输出的改写结果。
     * 解析失败时返回原文（不改写），不阻塞管道。
     */
    RewriteResult parseRewriteResult(String llmOutput, String original) {
        try {
            String json = llmOutput.trim()
                .replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.get("subQueries");
            if (arr != null && arr.isArray() && arr.size() > 0) {
                List<String> subQueries = new ArrayList<>();
                for (JsonNode node : arr) {
                    String sq = node.asText().trim();
                    if (!sq.isEmpty()) {
                        subQueries.add(sq);
                    }
                }
                if (!subQueries.isEmpty()) {
                    boolean actuallyChanged = subQueries.size() > 1
                        || !subQueries.get(0).equals(original);
                    if (actuallyChanged) {
                        log.info("[rewrite] '{}' → {}", original, subQueries);
                    }
                    return new RewriteResult(actuallyChanged, subQueries);
                }
            }
        } catch (Exception e) {
            log.warn("[rewrite] failed to parse LLM output: {}", e.getMessage());
        }
        return RewriteResult.unchanged(original);
    }

    // ── 内嵌类型 ──

    /**
     * 改写结果：含 1-N 个子问题。
     */
    public record RewriteResult(boolean rewritten, List<String> subQueries) {
        public static RewriteResult unchanged(String original) {
            return new RewriteResult(false, List.of(original));
        }
    }
}
