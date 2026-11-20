package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 文章可见性批量变更事件 —— 批量修改 visibility 后触发 RAG 文档状态同步。
 *
 * 与 {@link ArticleUpdatedEvent} 分开：
 *   1. 语义不同——只改可见性不改内容，走的是「软隐藏/零成本翻回」路径而非重建；
 *   2. 时序不同——监听器用 {@code @TransactionalEventListener(AFTER_COMMIT)}，
 *      等批量更新事务提交后再动 PG 向量，避免回滚造成 RAG 状态与文章状态不一致。
 *
 * articles 中每个 ArticleDO 的 visibility 已是目标值（发布事件前由调用方 set 好）。
 */
@Getter
public class ArticleVisibilityChangedEvent extends ApplicationEvent {

    private final List<ArticleDO> articles;

    /**
     * @param articles 可见性发生变更的文章列表。列表本身作为事件源（source）。
     */
    public ArticleVisibilityChangedEvent(List<ArticleDO> articles) {
        super(articles);
        this.articles = articles;
    }
}
