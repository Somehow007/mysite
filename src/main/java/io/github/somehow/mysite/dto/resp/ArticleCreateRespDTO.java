package io.github.somehow.mysite.dto.resp;

import lombok.Data;

/**
 * 创建文章请求返回体
 */
@Data
public class ArticleCreateRespDTO {

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
}
