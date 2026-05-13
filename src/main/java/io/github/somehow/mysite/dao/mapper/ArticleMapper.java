package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dto.req.article.ArticleFavoritePageQueryReqDTO;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import org.apache.ibatis.annotations.Param;

public interface ArticleMapper extends BaseMapper<ArticleDO> {

    int incrementViewCount(@Param("articleId") Long articleId, @Param("incrementNum") Integer incrementNum);

    int incrementFavoriteCount(@Param("articleId") Long articleId, @Param("incrementNum") Integer incrementNum);

    int decrementFavoriteCount(@Param("articleId") Long articleId, @Param("decrementNum") Integer decrementNum);

    IPage<ArticlePageQueryRespDTO> pageArticleResults(ArticlePageQueryReqDTO requestParam);

    IPage<ArticlePageQueryRespDTO> pageFavoriteArticleResults(ArticleFavoritePageQueryReqDTO requestParam);
}
