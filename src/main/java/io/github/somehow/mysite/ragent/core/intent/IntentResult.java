package io.github.somehow.mysite.ragent.core.intent;

import lombok.Builder;
import lombok.Data;

/**
 * 意图分类结果 DTO。
 * <p>
 * 由 IntentClassifier 产出，供 RagChatService 管道消费。
 * fallback() 返回 "全局检索" 的兜底结果——分类失败时宁可多搜也不少搜。
 */
@Data
@Builder
public class IntentResult {

    private Long intentId;
    private String type;                // KB_RETRIEVAL / CHAT
    private Long targetKbId;           // CHAT 类型为 null
    private double confidence;         // 0.0 ~ 1.0
    private boolean needsGuidance;     // LLM 判定需要引导用户澄清
    private String reason;             // 分类理由（日志/调试用）
    private String customPromptFragment;
    private Integer customTopK;

    /** 分类失败或意图列表为空时的兜底：全局检索 */
    public static IntentResult fallback() {
        return IntentResult.builder()
            .type("KB_RETRIEVAL")
            .targetKbId(null)          // null = 全局检索（不限定 KB）
            .confidence(0.0)
            .needsGuidance(false)
            .reason("fallback")
            .build();
    }

    /** 判断是否属于知识库检索类型 */
    public boolean isKbRetrieval() {
        return "KB_RETRIEVAL".equals(type);
    }

    /** 判断是否属于闲聊类型 */
    public boolean isChat() {
        return "CHAT".equals(type);
    }

    /** 判断置信度是否足够高，可以直接定向检索 */
    public boolean isHighConfidence() {
        return confidence >= 0.6;
    }
}
