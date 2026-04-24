package io.github.somehow.mysite.dto.req.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "分类排序请求")
public class CategorySortReqDTO {

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "排序值不能为空")
    @Schema(description = "排序值", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sortOrder;
}
