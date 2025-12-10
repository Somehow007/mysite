package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章评论数据库实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_comment")
public class CommentDO extends BaseDO {

    /**
     * 主键Id
     */
    private Long id;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论人ID
     */
    private Long userId;

    /**
     * 父评论Id，没有默认为 0
     */
    private Long parentId;

    /**
     * 评论的文章Id
     */
    private Long articleId;
}
