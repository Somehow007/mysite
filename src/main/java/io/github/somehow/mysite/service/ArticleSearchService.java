package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;

import java.util.List;

public interface ArticleSearchService {

    IPage<ArticlePageQueryRespDTO> searchArticles(ArticlePageQueryReqDTO requestParam);

    void indexArticle(ArticleDO article);

    void updateArticle(ArticleDO article);

    void deleteArticle(Long articleId);

    void syncAllArticles();

    long count();

    boolean isEnabled();
}
