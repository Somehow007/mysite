package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文章数据库实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_article")
public class ArticleDO extends BaseDO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章内容（通常为 HTML 或 Markdown 格式）
     */
    private String content;

    /**
     * 摘要/简介（可选，用于列表页展示）
     */
    private String summary;

    /**
     * 作者ID（关联 User 实体）
     */
    private Long authorId;

    /**
     * 是否发布（0:草稿 1:已发布）
     */
    private Integer published;

    /**
     * 阅读量
     */
    private Integer viewCount;

    /**
     * 收藏量
     */
    private Integer favoriteCount;

//    /**
//     * 评论列表（可选，用于级联加载）
//     */
//    private List<CommentDO> comments;

    // 所属分类（可选）
//    private Category category;

    // 标签列表（可选）
//    private List<Tag> tags;
}
