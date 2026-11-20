package io.github.somehow.mysite.ragent.ingestion;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.ragent.service.KnowledgeDocumentService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 ArticleCreatedEvent / ArticleUpdatedEvent，自动触发文档向量化。
 *
 * 为什么用 Spring Event 而不是直接在 ArticleService 里调？
 *   1. 解耦：Blog 模块不需要知道 RAG 模块的存在
 *   2. 异步：文档向量化需要调用外部 API（嵌入模型），不能让文章发布变慢
 *   3. 可测试：可以独立测试 RAG 模块，Mock Event 即可
 *
 * 注意：不在这一层加 @Async —— 异步在 KnowledgeDocumentService.syncArticle 上做，
 * 双重 @Async 没有收益，还会让异常栈和线程模型变复杂。
 */
@Component
public class ArticleEventListener {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public ArticleEventListener(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @EventListener
    public void handleArticleCreated(ArticleCreatedEvent event) {
        knowledgeDocumentService.syncArticle(event.getArticle());
    }

    @EventListener
    public void handleArticleUpdated(ArticleUpdatedEvent event) {
        knowledgeDocumentService.syncArticle(event.getArticle());
    }

    /**
     * 文章删除 → 异步清理 RAG 数据。
     *
     * 用 @TransactionalEventListener 而非 @EventListener：等文章删除事务提交后再清理，
     * 避免事务回滚却已删掉向量。fallbackExecution = true 覆盖无事务场景
     * （batchDeleteArticles 自调用 deleteArticle 时不经过代理，没有活动事务，此时立即执行）。
     */
    /**
     * 批量可见性变更 → 逐篇走 syncArticle 公开性门：
     * 转私有/草稿 → hideArticle 软隐藏（向量保留）；转公开 → hash 门禁零成本翻回或重建。
     *
     * 与删除事件同用 AFTER_COMMIT：等批量事务提交后再动 PG，回滚则不触发，
     * 保证 RAG 状态与文章状态一致。fallbackExecution 覆盖无事务调用方。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleVisibilityChanged(ArticleVisibilityChangedEvent event) {
        for (ArticleDO article : event.getArticles()) {
            knowledgeDocumentService.syncArticle(article);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleArticleDeleted(ArticleDeletedEvent event) {
        knowledgeDocumentService.removeArticle(event.getArticleId());
    }
}
