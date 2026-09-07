package io.github.somehow.mysite.ragent.usage;

public record TokenUsage(int promptTokens, int completionTokens, int totalTokens, boolean fromApi) {

    public static TokenUsage api(int prompt, int completion, int total) {
        int t = total > 0 ? total : prompt + completion;
        return new TokenUsage(Math.max(0, prompt), Math.max(0, completion), Math.max(0, t), true);
    }

    public static TokenUsage estimated(int prompt, int completion) {
        int p = Math.max(0, prompt);
        int c = Math.max(0, completion);
        return new TokenUsage(p, c, p + c, false);
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
