package io.github.somehow.mysite.ragent.core.intent;

import lombok.Builder;
import lombok.Data;

/**
 * 意图分类结果 DTO。
 * <p>
 * 由 IntentClassifier 产出，供 RagChatService 管道消费。
 * 三类模式：KB_META（目录/统计）、KB_RETRIEVAL（内容检索）、CHAT（闲聊）。
 */
@Data
@Builder
public class IntentResult {

    private Long intentId;
    private String type;                // KB_META / KB_RETRIEVAL / CHAT / MCP
    private Long targetKbId;           // 历史字段，管道不按它选库
    private double confidence;         // 0.0 ~ 1.0
    private boolean needsGuidance;     // LLM 判定需要引导用户澄清（本轮不消费）
    private String reason;             // 分类理由（日志/调试用）
    private String customPromptFragment;
    private Integer customTopK;

    /** 分类失败或低置信度：有选 KB 则走内容检索，否则闲聊。 */
    public static IntentResult fallback(boolean hasKbIds) {
        if (hasKbIds) {
            return IntentResult.builder()
                .type("KB_RETRIEVAL")
                .targetKbId(null)
                .confidence(0.0)
                .needsGuidance(false)
                .reason("fallback")
                .build();
        }
        return IntentResult.builder()
            .type("CHAT")
            .targetKbId(null)
            .confidence(0.0)
            .needsGuidance(false)
            .reason("fallback")
            .build();
    }

    /** @deprecated 使用 {@link #fallback(boolean)} */
    public static IntentResult fallback() {
        return fallback(false);
    }

    public boolean isKbMeta() {
        return "KB_META".equals(type);
    }

    public boolean isKbRetrieval() {
        return "KB_RETRIEVAL".equals(type);
    }

    public boolean isChat() {
        return "CHAT".equals(type);
    }

    public boolean isMcp() {
        return "MCP".equals(type);
    }

    public boolean isHighConfidence() {
        return confidence >= 0.6;
    }
}
