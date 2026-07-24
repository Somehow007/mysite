package io.github.somehow.mysite.ragent.core.intent;

import io.github.somehow.mysite.ragent.dao.entity.IntentDO;
import io.github.somehow.mysite.ragent.llm.model.ChatEvent;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 歧义引导处理器 —— Ragent 的 guidance 包精简版。
 *
 * <h3>触发条件</h3>
 * 当意图分类器遇到以下情况时，不盲目检索，而是生成引导选项让用户点选：
 * <ol>
 *   <li>confidence &lt; 0.6 —— 分类器自己都不确定</li>
 *   <li>needsGuidance == true —— LLM 明确标记的歧义场景</li>
 *   <li>top-2 意图分数比 ≥ 0.75 —— 两个意图非常接近</li>
 * </ol>
 *
 * <h3>交互模式</h3>
 * 前端收到 guidance 事件后渲染可点击选项按钮，用户点击后带上 chosenIntentId
 * 重新请求，后端直接按指定意图走管道，跳过分类环节。
 */
@Slf4j
@Component
public class GuidanceHandler {

    /**
     * 检测是否需要引导用户澄清意图。
     *
     * @param result        分类器返回的 top-1 结果
     * @param topCandidates 所有候选意图（按置信度降序），用于检查 top-2 是否接近
     * @return true = 需要引导，false = 可直接按 result 走管道
     */
    public boolean shouldGuide(IntentResult result, List<IntentCandidate> topCandidates) {
        if (result == null) return false;
        if (result.isNeedsGuidance()) return true;
        if (result.getConfidence() < 0.6) return true;

        // 检查 top-2 分数比：如果第二名的分数 ≥ 第一名的 75%，说明两个意图非常接近
        if (topCandidates != null && topCandidates.size() >= 2) {
            double top1 = topCandidates.get(0).score;
            double top2 = topCandidates.get(1).score;
            if (top1 > 0 && top2 / top1 >= 0.75) {
                log.info("[guidance] top-2 intents too close: {} ({}) vs {} ({})",
                    topCandidates.get(0).intentName, String.format("%.2f", top1),
                    topCandidates.get(1).intentName, String.format("%.2f", top2));
                return true;
            }
        }
        return false;
    }

    /**
     * 生成引导 SSE 事件。
     * <p>
     * 前端收到的 guidance 事件格式：
     * <pre>
     * {"type":"guidance","message":"我不太确定你想问什么…",
     *  "options":[{"label":"技术博客中的 Spring 框架教程","intentId":1},
     *             {"label":"读书笔记中的《Spring 实战》书评","intentId":2}]}
     * </pre>
     *
     * @param candidates 候选意图列表（已按优先级/置信度排序，取前 2-3 个）
     * @return ChatEvent（type=guidance）
     */
    public ChatEvent buildGuidanceEvent(List<IntentCandidate> candidates) {
        String message;
        if (candidates.size() > 1) {
            message = "我不太确定你想了解哪方面的内容，请选择一个方向：";
        } else {
            message = "你问的问题有些模糊，能说得更具体一点吗？";
        }

        List<ChatEvent.GuidanceOption> options = new ArrayList<>();
        for (IntentCandidate c : candidates) {
            options.add(new ChatEvent.GuidanceOption(c.intentName, c.intentId));
        }

        return ChatEvent.guidance(message, options);
    }

    /**
     * 模拟多候选排序（简化版：不额外调 LLM，直接用意图本身的 priority 排序）。
     * <p>
     * 更完整的实现（如 Ragent）会调 LLM 对所有意图打分，但博客场景意图极少，
     * 用分类器返回的 confidence + 意图自身的 priority 双重排序就够了。
     *
     * @param intents 所有已启用的意图
     * @return 按优先级降序排列的候选列表
     */
    public List<IntentCandidate> rankCandidates(List<IntentDO> intents) {
        return intents.stream()
            .sorted(Comparator.comparingInt(IntentDO::getPriority).reversed())
            .map(i -> IntentCandidate.builder()
                .intentId(i.getId())
                .intentName(i.getName())
                .score(i.getPriority() / 10.0)  // 归一化：priority 10 → score 1.0
                .build())
            .toList();
    }

    // ── 内嵌类型 ──

    @Data
    @Builder
    public static class IntentCandidate {
        private Long intentId;
        private String intentName;
        private double score;
    }
}
