package io.github.somehow.mysite.ragent.usage;

public record TokenUsage(int promptTokens, int completionTokens, int totalTokens,
                         int cacheReadTokens, int cacheWriteTokens, boolean fromApi) {

    public static TokenUsage api(int prompt, int completion, int total) {
        return api(prompt, completion, total, 0, 0);
    }

    public static TokenUsage api(int prompt, int completion, int total, int cacheRead, int cacheWrite) {
        int t = total > 0 ? total : prompt + completion;
        int p = Math.max(0, prompt);
        // cacheRead 是 promptTokens 的子集（命中部分），钳制防止厂商口径不一致时出现负的未命中量
        int cr = Math.min(Math.max(0, cacheRead), p);
        int cw = Math.max(0, cacheWrite);
        return new TokenUsage(p, Math.max(0, completion), Math.max(0, t), cr, cw, true);
    }

    public static TokenUsage estimated(int prompt, int completion) {
        int p = Math.max(0, prompt);
        int c = Math.max(0, completion);
        return new TokenUsage(p, c, p + c, 0, 0, false);
    }

    /** 中文为主文本的粗估：chars × 0.6。 */
    public static int estimateTokens(int chars) {
        if (chars <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(chars * 0.6));
    }

    public static int estimateTokens(String text) {
        return estimateTokens(text == null ? 0 : text.length());
    }
}
