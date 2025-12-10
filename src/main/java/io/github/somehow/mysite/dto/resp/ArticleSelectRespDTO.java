package io.github.somehow.mysite.dto.resp;

import lombok.Data;

/**
 * 查询单个文章返回实体
 */
@Data
public class ArticleSelectRespDTO {

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
     * 阅读量
     */
    private Integer viewCount;

    /**
     * 收藏量
     */
    private Integer favoriteCount;
}
