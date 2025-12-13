package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建文章请求体
 */
@Data
public class ArticleCreateReqDTO {

    /**
     * 作者id
     */
    @Schema(description = "作者id", example = "1992826310106120192", requiredMode = Schema.RequiredMode.REQUIRED)
    private String authorId;

    /**
     * 作者名称
     */
    @Schema(description = "作者名称", example = "Somehow007", requiredMode = Schema.RequiredMode.REQUIRED)
    private String authorName;

    /**
     * 文章标题
     */
    @Schema(description = "文章标题",
            example = "如何一夜暴富迎娶白富美",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /**
     * 文章内容（通常为 HTML 或 Markdown 格式）
     */
    @Schema(description = "文章内容",
            example = "睡一觉",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    /**
     * 摘要/简介（可选，用于列表页展示）
     */
    @Schema(description = "文章摘要",
            example = "彩！彩！彩！",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String summary;
}
