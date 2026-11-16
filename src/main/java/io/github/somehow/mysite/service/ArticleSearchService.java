package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;

import java.util.List;

public interface ArticleSearchService {

    /**
     * 解析生效的 published 过滤值（DB/ES 两个实现共用，防规则漂移）：
     * <ul>
     *   <li>非 ADMIN/CREATOR → 恒 1（published 参数被忽略，防拉草稿）</li>
     *   <li>管理视图 + published 显式传入 → 按参数过滤</li>
     *   <li>管理视图 + includeDraft=true 且未传 published → null（不过滤，查全量）</li>
     *   <li>其余 → 1（管理视图默认也只看已发布）</li>
     * </ul>
     */
    static Integer resolvePublishedFilter(ArticlePageQueryReqDTO req) {
        UserRole role = UserContext.getRole();
        boolean managementView = UserRole.ADMIN.equals(role) || UserRole.CREATOR.equals(role);
        if (!managementView) {
            return 1;
        }
        if (req.getPublished() != null) {
            return req.getPublished();
        }
        return Boolean.TRUE.equals(req.getIncludeDraft()) ? null : 1;
    }


    IPage<ArticlePageQueryRespDTO> searchArticles(ArticlePageQueryReqDTO requestParam);

    void indexArticle(ArticleDO article);

    void updateArticle(ArticleDO article);

    void deleteArticle(Long articleId);

    void syncAllArticles();

    long count();

    boolean isEnabled();
}
