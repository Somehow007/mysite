package io.github.somehow.mysite.ragent.core.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.dao.entity.IntentDO;
import io.github.somehow.mysite.ragent.dao.mapper.IntentMapper;
import io.github.somehow.mysite.ragent.llm.LLMService;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.usage.UsageContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 意图分类器 —— 三类模式：KB_META / KB_RETRIEVAL / CHAT。
 *
 * <ol>
 *   <li>关键词快路径：恰好一个启用意图的 keywords 命中则跳过 LLM</li>
 *   <li>cheap LLM 做 JSON 分类（temperature=0）</li>
 *   <li>解析失败或置信度 &lt; 0.5 时按是否已选 KB 降级，不阻塞管道</li>
 * </ol>
 */
@Slf4j
@Component
public class IntentClassifier {

    static final double MIN_CONFIDENCE = 0.5;
    static final double KEYWORD_CONFIDENCE = 0.95;

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

    public IntentResult classify(String question, List<ChatMessage> history) {
        return classify(question, history, false);
    }

    /**
     * @param question  用户当前问题（可能是改写后的主查询）
     * @param history   最近几轮对话
     * @param hasKbIds  用户是否勾选了知识库（影响低置信度降级）
     */
    public IntentResult classify(String question, List<ChatMessage> history, boolean hasKbIds) {
        List<IntentDO> intents = intentMapper.listEnabled();

        if (intents.isEmpty()) {
            log.debug("[intent] no intents configured, using fallback");
            return IntentResult.fallback(hasKbIds);
        }

        IntentResult keywordHit = matchByKeywords(question, intents);
        if (keywordHit != null) {
            log.info("[intent] keyword fast-path: '{}' (type={}, confidence={})",
                keywordHit.getReason(), keywordHit.getType(), keywordHit.getConfidence());
            return keywordHit;
        }

        String classificationPrompt = buildClassificationPrompt(intents, question, history);

        try {
            UsageContext.setCallType("CLASSIFY");
            String llmOutput = classificationLLM.chat(
                ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(classificationPrompt)))
                    .temperature(0.0)
                    .maxTokens(256)
                    .build());
            return parseIntentResult(llmOutput, intents, hasKbIds);
        } catch (Exception e) {
            log.warn("[intent] classification LLM call failed: {}", e.getMessage());
            return IntentResult.fallback(hasKbIds);
        }
    }

    /**
     * 恰好一个启用意图的关键词命中时走快路径；0 个或多个冲突则返回 null 交给 LLM。
     * CHAT 只在整句几乎就是问候/感谢时命中，避免「谢谢，JWT 怎么配」被短路。
     */
    IntentResult matchByKeywords(String question, List<IntentDO> intents) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String q = question.toLowerCase(Locale.ROOT);
        List<IntentDO> hits = new ArrayList<>();
        for (IntentDO intent : intents) {
            if (keywordsHit(q, intent)) {
                hits.add(intent);
            }
        }
        if (hits.size() != 1) {
            return null;
        }
        IntentDO matched = hits.get(0);
        return IntentResult.builder()
            .intentId(matched.getId())
            .type(matched.getType())
            .targetKbId(matched.getKbId())
            .confidence(KEYWORD_CONFIDENCE)
            .needsGuidance(false)
            .reason("keyword:" + matched.getName())
            .customPromptFragment(matched.getCustomPromptFragment())
            .customTopK(matched.getCustomTopK())
            .build();
    }

    /** 去掉空白和标点后超过此长度的句子，不当成闲聊问候。 */
    static final int CHAT_FAST_PATH_MAX_LEN = 8;

    boolean keywordsHit(String questionLower, IntentDO intent) {
        List<String> keywords = parseKeywords(intent.getKeywords());
        if (keywords.isEmpty()) {
            return false;
        }
        String haystack = questionLower;
        if ("CHAT".equals(intent.getType())) {
            haystack = compact(questionLower);
            if (haystack.length() > CHAT_FAST_PATH_MAX_LEN) {
                return false;
            }
        }
        for (String kw : keywords) {
            if (!kw.isBlank() && haystack.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static String compact(String question) {
        return question.replaceAll("[\\s\\p{Punct}，。！？、；：\"'“”‘’（）()【】《》…—·]+", "");
    }

    List<String> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(keywordsJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    out.add(item.asText());
                }
            }
            return out;
        } catch (Exception e) {
            log.debug("[intent] skip malformed keywords: {}", e.getMessage());
            return List.of();
        }
    }

    String buildClassificationPrompt(List<IntentDO> intents, String question,
                                     List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            你是一个意图分类器。根据用户问题判断它属于以下哪个意图。只选一个。

            三类模式的边界（必须遵守）：
            - KB_META：问知识库本身——有多少篇文章、谁写的/谁上传的、谁最后上传、最近入库了什么、库里有哪些文章。禁止当成内容检索。
            - KB_RETRIEVAL：问文章里的技术内容、实现方法、概念、代码或配置。
            - CHAT：问候、感谢、你是谁、与博客内容和知识库目录都无关的闲聊。

            输出格式（严格 JSON，不要 markdown code block）：
            {"intentId": <数字>, "confidence": <0.0-1.0>, "reason": "<一句话理由>",
             "needsGuidance": <true|false>}

            needsGuidance = true 的情况：
            - 问题过于模糊/简短，无法确定用户真正想问什么
            - 问题有歧义，可能匹配多个意图且置信度接近
            - 问题含代词但缺少上下文

            ## 候选意图列表
            """);

        for (IntentDO intent : intents) {
            sb.append("- ID=%d | 类型=%s | 名称=%s | 描述=%s\n".formatted(
                intent.getId(), intent.getType(), intent.getName(), intent.getDescription()));
        }

        if (history != null && !history.isEmpty()) {
            sb.append("\n## 对话历史（最近 2 轮）\n");
            int start = Math.max(0, history.size() - 4);
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
     * 解析 LLM 输出。格式错误、未知 intentId、置信度过低都降级，不阻塞管道。
     */
    IntentResult parseIntentResult(String llmOutput, List<IntentDO> intents, boolean hasKbIds) {
        try {
            String json = extractJson(llmOutput);
            JsonNode root = objectMapper.readTree(json);
            long intentId = root.get("intentId").asLong();
            double confidence = clamp(root.get("confidence").asDouble(), 0.0, 1.0);
            String reason = root.path("reason").asText("");
            boolean needsGuidance = root.path("needsGuidance").asBoolean(false);

            if (confidence < MIN_CONFIDENCE) {
                log.info("[intent] low confidence={}, falling back (hasKbIds={})",
                    confidence, hasKbIds);
                return IntentResult.fallback(hasKbIds);
            }

            IntentDO matched = intents.stream()
                .filter(i -> i.getId() != null && i.getId() == intentId)
                .findFirst()
                .orElse(null);

            if (matched == null) {
                log.warn("[intent] LLM returned unknown intentId={}, falling back", intentId);
                return IntentResult.fallback(hasKbIds);
            }

            log.info("[intent] classified as '{}' (type={}, confidence={})",
                matched.getName(), matched.getType(), String.format("%.2f", confidence));

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
            return IntentResult.fallback(hasKbIds);
        }
    }

    static String extractJson(String llmOutput) {
        if (llmOutput == null) {
            return "";
        }
        String json = llmOutput.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return json.substring(start, end + 1);
        }
        return json;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
