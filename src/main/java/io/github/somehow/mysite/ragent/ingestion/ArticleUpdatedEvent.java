package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文章更新事件 —— 文章修改后触发 RAG 文档重新向量化。
 * 处理逻辑与创建事件相同（先删旧向量再重新分块嵌入），
 * 分为两个事件类是出于语义清晰考虑：未来可能对"创建"和"更新"做不同处理。
 */
@Getter
public class ArticleUpdatedEvent extends ApplicationEvent {

    private final ArticleDO article;

    /**
     * @param article 被更新的文章。article 本身作为事件源（source）。
     */
    public ArticleUpdatedEvent(ArticleDO article) {
        super(article);
        this.article = article;
    }
}
