package io.github.somehow.mysite.ragent.core;

/**
 * 用户正在阅读的文章上下文（文章页聊天场景）。
 *
 * <p>仅在前端传入 articleId 时加载；非文章页不传即为 null，管道行为与原先完全一致。
 * 「这篇 / 本文 / 该文」等指代默认指向此文章；询问其他文章时仍走知识库检索。</p>
 */
public record ReadingContext(Long articleId, String title, String content) {

    /** 注入 Prompt 的正文上限，避免超长文章撑爆上下文窗口 */
    public static final int MAX_CONTENT_CHARS = 10_000;

    public static ReadingContext of(Long articleId, String title, String content) {
        String safeTitle = title == null ? "" : title.trim();
        String safeContent = content == null ? "" : content;
        if (safeContent.length() > MAX_CONTENT_CHARS) {
            safeContent = safeContent.substring(0, MAX_CONTENT_CHARS) + "\n\n（正文过长，已截断）";
        }
        return new ReadingContext(articleId, safeTitle, safeContent);
    }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
