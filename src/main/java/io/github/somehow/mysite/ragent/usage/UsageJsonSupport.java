package io.github.somehow.mysite.ragent.usage;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 从各家 OpenAI 兼容 usage JSON 中提取缓存 token 字段。
 * <p>
 * 各家字段名不同但语义一致（cacheRead 是 promptTokens 的子集）：
 * <ul>
 *   <li>OpenAI 系：{@code usage.prompt_tokens_details.cached_tokens}</li>
 *   <li>DeepSeek：{@code usage.prompt_cache_hit_tokens}（prompt = hit + miss）</li>
 *   <li>Anthropic 风格（防御性）：{@code cache_read_input_tokens} / {@code cache_creation_input_tokens}</li>
 * </ul>
 * 多字段并存时取最大值——它们是同一指标的不同 schema，不叠加。
 */
public final class UsageJsonSupport {

    private UsageJsonSupport() {
    }

    /** 缓存命中（读取）token 数；无任何字段返回 0。 */
    public static int cacheReadTokens(JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return 0;
        }
        int v = 0;
        JsonNode details = usage.path("prompt_tokens_details");
        if (details.isObject()) {
            v = Math.max(v, details.path("cached_tokens").asInt(0));
        }
        v = Math.max(v, usage.path("prompt_cache_hit_tokens").asInt(0));
        v = Math.max(v, usage.path("cache_read_input_tokens").asInt(0));
        return Math.max(0, v);
    }

    /** 缓存写入 token 数；目前仅 Anthropic 风格有此概念，其余厂商返回 0。 */
    public static int cacheWriteTokens(JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return 0;
        }
        return Math.max(0, usage.path("cache_creation_input_tokens").asInt(0));
    }
}
