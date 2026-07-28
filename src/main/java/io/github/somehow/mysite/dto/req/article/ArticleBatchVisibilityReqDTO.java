package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量修改文章可见性请求")
public class ArticleBatchVisibilityReqDTO {

    @NotEmpty(message = "文章ID列表不能为空")
    @Schema(description = "文章ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotNull(message = "可见性不能为空")
    @Min(value = 0, message = "可见性只能为 0（公开）或 1（仅自己可见）")
    @Max(value = 1, message = "可见性只能为 0（公开）或 1（仅自己可见）")
    @Schema(description = "可见性：0=公开，1=仅自己可见", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer visibility;
}
