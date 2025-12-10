package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.CommentDO;
import io.github.somehow.mysite.dto.req.comment.CommentCreateReqDTO;
import io.github.somehow.mysite.dto.resp.comment.CommentPageQueryRespDTO;

/**
 * 评论业务逻辑层
 */
public interface CommentService extends IService<CommentDO> {

    /**
     * 发起评论
     *
     * @param requestParam 请求参数
     */
    void createComment(CommentCreateReqDTO requestParam);

    /**
     * 删除评论
     *
     * @param id 评论Id
     */
    void deleteComment(String id);

    /**
     * 分页查询某文章的所有评论，优先展开一级评论
     *
     * @param articleId 请求参数，文章id
     * @return  返回实体
     */
    IPage<CommentPageQueryRespDTO> pageQueryComments(String articleId);
}
