package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文章创建事件 —— 发布后触发 RAG 文档向量化。
 *
 * 为什么用 Spring Event 而不是直接在 ArticleService 里调？
 *   1. 解耦：Blog 模块不需要知道 RAG 模块的存在
 *   2. 异步：文档向量化需要调用外部 API（嵌入模型），不能让文章发布变慢
 *   3. 可测试：可以独立测试 RAG 模块，Mock Event 即可
 */
@Getter
public class ArticleCreatedEvent extends ApplicationEvent {

    private final ArticleDO article;

    /**
     * @param article 被创建的文章。article 本身作为事件源（source）。
     */
    public ArticleCreatedEvent(ArticleDO article) {
        super(article);
        this.article = article;
    }
}
