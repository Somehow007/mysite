package io.github.somehow.mysite.dto.req.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "创建分类请求")
public class CategoryCreateReqDTO {

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称", example = "技术", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "分类别名不能为空")
    @Schema(description = "URL友好别名", example = "tech", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;

    @Schema(description = "分类描述", example = "技术相关文章")
    private String description;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "父分类ID", example = "null")
    private Long parentId;

    @Min(value = 1, message = "分类层级最小为1")
    @Max(value = 3, message = "分类层级最大为3")
    @Schema(description = "分类层级 1:一级 2:二级 3:三级", example = "1")
    private Integer level;

    @Schema(description = "状态 0:禁用 1:启用", example = "1")
    private Integer status;

    @Schema(description = "分类图标", example = "tech-icon")
    private String icon;

    @Schema(description = "分类颜色", example = "#1890ff")
    private String color;

    @Schema(description = "SEO标题", example = "技术文章分类")
    private String seoTitle;

    @Schema(description = "SEO描述", example = "技术相关文章分类")
    private String seoDescription;

    @Schema(description = "SEO关键词", example = "技术,编程,开发")
    private String seoKeywords;
}
