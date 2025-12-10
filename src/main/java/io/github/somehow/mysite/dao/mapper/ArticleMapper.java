package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dto.req.article.ArticleFavoritePageQueryReqDTO;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 文章数据库持久层
 */
public interface ArticleMapper extends BaseMapper<ArticleDO> {

    /**
     * 增加阅读量
     *
     * @param articleId 文章id
     * @param incrementNum 增加数量
     * @return 影响的行数，为0则失败
     */
    int incrementViewCount(@Param("articleId") Long articleId, @Param("incrementNum") Integer incrementNum);

    /**
     * 增加收藏量
     *
     * @param articleId 文章id
     * @param incrementNum 增加数量
     * @return 影响的行数，为0则失败
     */
    int incrementFavoriteCount(@Param("articleId") Long articleId, @Param("incrementNum") Integer incrementNum);

    /**
     * 减少收藏量
     *
     * @param articleId 文章id
     * @param decrementNum 减少数量
     * @return 影响的行数，为0则失败
     */
    int decrementFavoriteCount(@Param("articleId") Long articleId, @Param("decrementNum") Integer decrementNum);

    /**
     * 分页查询文章信息返回实体
     *
     * @param requestParam 请求参数
     */
    IPage<ArticlePageQueryRespDTO> pageArticleResults(ArticlePageQueryReqDTO requestParam);

    /**
     * 分页查询文章信息返回实体
     *
     * @param requestParam 请求参数
     */
    IPage<ArticlePageQueryRespDTO> pageFavoriteArticleResults(ArticleFavoritePageQueryReqDTO requestParam);
}
