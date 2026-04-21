package io.github.somehow.mysite.dto.req.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
}
