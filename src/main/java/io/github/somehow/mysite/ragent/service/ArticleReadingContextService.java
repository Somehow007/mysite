package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.ragent.core.ReadingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 从文章页聊天请求解析「当前正在阅读的文章」上下文。
 *
 * <p>可见性与文章详情一致：已发布 +（公开 或 作者本人/管理员）。加载失败返回 null，
 * 不阻塞聊天——降级为无阅读上下文的常规对话。</p>
 */
@Slf4j
@Service
public class ArticleReadingContextService {

    private final ArticleMapper articleMapper;

    public ArticleReadingContextService(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    /**
     * @param articleId 前端文章页传入的文章 ID；null 直接返回 null
     * @return 可注入 Prompt 的阅读上下文；文章不存在/不可见/未发布时返回 null
     */
    public ReadingContext load(Long articleId) {
        if (articleId == null) {
            return null;
        }
        try {
            ArticleDO article = articleMapper.selectById(articleId);
            if (article == null) {
                log.info("[reading-ctx] article not found: {}", articleId);
                return null;
            }
            if (!isViewable(article)) {
                log.info("[reading-ctx] article not viewable: {}", articleId);
                return null;
            }
            return ReadingContext.of(article.getId(), article.getTitle(), article.getContent());
        } catch (Exception e) {
            log.warn("[reading-ctx] load failed for article {}: {}", articleId, e.getMessage());
            return null;
        }
    }

    private boolean isViewable(ArticleDO article) {
        if (article.getPublished() != null && article.getPublished() != 1) {
            return false;
        }
        // visibility: 0=公开 1=仅自己可见
        Integer visibility = article.getVisibility();
        if (visibility == null || visibility == 0) {
            return true;
        }
        if (UserContext.isAdmin()) {
            return true;
        }
        String currentUserId = UserContext.getUserId();
        return currentUserId != null
            && article.getAuthorId() != null
            && currentUserId.equals(String.valueOf(article.getAuthorId()));
    }
}
