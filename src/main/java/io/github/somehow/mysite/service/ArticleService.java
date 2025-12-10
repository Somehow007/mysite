package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;

/**
 * 文章业务逻辑层
 */
public interface ArticleService extends IService<ArticleDO> {

    /**
     * 创建文章
     * 可选择是否保存为草稿，设置为1
     * 点击发布进行更新即可
     * 每次编辑后不保存，必须在一次内完成更新
     *
     * @param requestParam  请求参数
     */
    void createArticle(ArticleCreateReqDTO requestParam);

    /**
     * 更新文章
     *
     * @param requestParam  请求参数
     */
    void updateArticle(ArticleUpdateReqDTO requestParam);

    /**
     * 删除文章
     *
     * @param id 文章id
     */
    void deleteArticle(Long id);

    /**
     * 分页获取文章
     *
     * @param requestParam 请求参数
     */
    IPage<ArticlePageQueryRespDTO> pageQueryArticle(ArticlePageQueryReqDTO requestParam);

    /**
     * 分页获取用户收藏的文章
     *
     * @param requestParam 请求参数
     */
    IPage<ArticlePageQueryRespDTO> pageQueryFavoriteArticle(ArticleFavoritePageQueryReqDTO requestParam);

    /**
     * 查询具体文章信息（点击进入）
     *
     * @param id 文章id
     * @return  文章信息
     */
    ArticleSelectRespDTO selectOneArticle(Long id);

    /**
     * 收藏或取消收藏文章
     *
     * @param requestParam 请求参数
     */
    void favoriteArticle(ArticleFavoriteReqDTO requestParam);

}
