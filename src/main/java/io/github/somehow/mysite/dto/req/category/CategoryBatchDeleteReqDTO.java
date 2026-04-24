package io.github.somehow.mysite.dto.req.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量删除分类请求")
public class CategoryBatchDeleteReqDTO {

    @NotEmpty(message = "分类ID列表不能为空")
    @Schema(description = "分类ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
