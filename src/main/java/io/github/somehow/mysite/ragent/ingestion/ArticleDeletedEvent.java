package io.github.somehow.mysite.ragent.ingestion;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文章删除事件 —— 文章删除后触发 RAG 知识库中对应文档/分块/向量的异步清理。
 *
 * 与 {@link ArticleCreatedEvent} / {@link ArticleUpdatedEvent} 不同，只携带 articleId：
 * 文章行已（逻辑）删除，清理 RAG 数据只需要 source_ref（即 articleId），无需整篇文章对象。
 */
@Getter
public class ArticleDeletedEvent extends ApplicationEvent {

    private final Long articleId;

    /**
     * @param articleId 被删除文章的 ID。articleId 本身作为事件源（source）。
     */
    public ArticleDeletedEvent(Long articleId) {
        super(articleId);
        this.articleId = articleId;
    }
}
