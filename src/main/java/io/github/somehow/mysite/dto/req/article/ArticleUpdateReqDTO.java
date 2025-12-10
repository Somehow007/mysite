package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新文章请求实体
 */
@Data
@Schema(description = "更新文章请求实体")
public class ArticleUpdateReqDTO {

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
     * 是否发布（0:草稿 1:已发布）
     */
    private Integer published;

}
