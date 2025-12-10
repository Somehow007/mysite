package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.CommentDO;
import io.github.somehow.mysite.dto.resp.comment.CommentPageQueryRespDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 评论数据库持久层
 */
public interface CommentMapper extends BaseMapper<CommentDO> {

    /**
     * 分页查询某文章的所有评论
     *
     * @param articleId 文章Id
     */
    IPage<CommentPageQueryRespDTO> pageCommentResults(@Param("articleId") Long articleId);
}
