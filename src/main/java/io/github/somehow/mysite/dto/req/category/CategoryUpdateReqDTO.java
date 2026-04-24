package io.github.somehow.mysite.dto.req.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "更新分类请求")
public class CategoryUpdateReqDTO {

    private String name;
    private String slug;
    private String description;
    private Integer sortOrder;
    private Long parentId;

    @Min(value = 1, message = "分类层级最小为1")
    @Max(value = 3, message = "分类层级最大为3")
    private Integer level;

    private Integer status;
    private String icon;
    private String color;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
}
