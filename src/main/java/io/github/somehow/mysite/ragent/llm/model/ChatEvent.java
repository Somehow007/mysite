package io.github.somehow.mysite.ragent.llm.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.somehow.mysite.ragent.dto.SourceChunkDTO;

import java.util.List;

/**
 * SSE 事件模型 —— RagChatService 与前端之间的统一协议。
 *
 * <h3>事件序列（一次问答）</h3>
 * <pre>
 *   meta     ×1 → 流开始时发，携带 conversationId
 *   sources  ×1 → 检索到的引用来源（在 content 之前发）
 *   content  ×N → 每个 token 一条
 *   done     ×1 → 正常结束
 *   error    ×1 → 出错时发（替代裸断开）
 *   guidance ×1 → 歧义引导（Phase 6 新增）
 * </pre>
 *
 * @param type           事件类型：meta / sources / content / done / error / guidance
 * @param delta          content 事件的 token 文本
 * @param sources        sources 事件的引用来源列表
 * @param conversationId meta 事件的会话 ID（序列化为 string 避免 JS Number 精度丢失）
 * @param message        error 事件的错误信息 / guidance 事件的引导文案
 * @param options        guidance 事件的引导选项列表
 */
public record ChatEvent(
    String type,
    String delta,
    List<SourceChunkDTO> sources,
    @JsonSerialize(using = ToStringSerializer.class)
    Long conversationId,
    String message,
    List<GuidanceOption> options
) {
    // ── 标准事件工厂 ──

    public static ChatEvent meta(Long conversationId) {
        return new ChatEvent("meta", null, null, conversationId, null, null);
    }

    public static ChatEvent sources(List<SourceChunkDTO> sources) {
        return new ChatEvent("sources", null, sources, null, null, null);
    }

    public static ChatEvent content(String delta) {
        return new ChatEvent("content", delta, null, null, null, null);
    }

    public static ChatEvent done() {
        return new ChatEvent("done", null, null, null, null, null);
    }

    public static ChatEvent error(String message) {
        return new ChatEvent("error", null, null, null, message, null);
    }

    // ── Phase 6: 歧义引导事件 ──

    /**
     * 歧义引导事件 —— 当系统不确定用户意图时，生成引导选项让用户点选。
     *
     * <pre>
     * 前端交互流程：
     *   1. 收到 type="guidance" → 解析 options 数组
     *   2. 渲染为可点击的选项按钮
     *   3. 用户点击按钮 → 带上 chosenIntentId 重新请求
     *   4. 后端直接按指定 intent 走管道
     * </pre>
     */
    public static ChatEvent guidance(String message, List<GuidanceOption> options) {
        return new ChatEvent("guidance", null, null, null, message, options);
    }

    // ── 引导选项 ──

    /**
     * 单个引导选项。
     *
     * @param label    展示文本，如 "技术博客中的 Spring 框架教程"
     * @param intentId 对应的意图 ID（用户选择后回传）
     */
    public record GuidanceOption(String label, Long intentId) {}
}
