package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ArticleCreateReqDTO {

    @Schema(description = "作者id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "作者ID不能为空")
    private String authorId;

    @Schema(description = "文章标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文章标题不能为空")
    private String title;

    @Schema(description = "文章内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文章内容不能为空")
    private String content;

    @Schema(description = "文章摘要")
    private String summary;

    @Schema(description = "封面图片URL")
    private String coverImage;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "标签ID列表")
    private List<Long> tagIds;

    @Schema(description = "是否发布: 0-草稿, 1-发布")
    private Integer published;

    @Schema(description = "所属合集ID（可选，新建文章时自动加入指定合集）")
    private Long collectionId;
}
