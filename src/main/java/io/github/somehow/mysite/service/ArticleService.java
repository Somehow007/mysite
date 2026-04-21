package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArchiveRespDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;

import java.util.List;

public interface ArticleService extends IService<ArticleDO> {

    void createArticle(ArticleCreateReqDTO requestParam);
    void updateArticle(ArticleUpdateReqDTO requestParam);
    void deleteArticle(Long id);
    IPage<ArticlePageQueryRespDTO> pageQueryArticle(ArticlePageQueryReqDTO requestParam);
    IPage<ArticlePageQueryRespDTO> pageQueryFavoriteArticle(ArticleFavoritePageQueryReqDTO requestParam);
    ArticleSelectRespDTO selectOneArticle(Long id);
    void favoriteArticle(ArticleFavoriteReqDTO requestParam);
    List<ArchiveRespDTO> getArchive();
}
