package io.github.somehow.mysite.dto.resp.comment;

import lombok.Data;

/**
 * 分页查询某文章评论内容返回实体
 */
@Data
public class CommentPageQueryRespDTO {

    /**
     * 评论Id
     */
    private Long commentId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论人Id
     */
    private Long userId;

    /**
     * 评论人昵称
     */
    private String username;

    /**
     * 父评论Id，没有默认为 null
     */
    private Long parentId;

    /**
     * 评论的文章Id
     */
    private Long articleId;
}
