package io.github.somehow.mysite.dto.req.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CollectionCreateReqDTO {

    @Schema(description = "合集标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "合集标题不能为空")
    @Size(max = 200, message = "合集标题不能超过200个字符")
    private String title;

    @Schema(description = "合集描述")
    @Size(max = 500, message = "合集描述不能超过500个字符")
    private String description;

    @Schema(description = "合集封面图片URL")
    @Size(max = 500, message = "封面图片URL不能超过500个字符")
    private String coverImage;

    @Schema(description = "排序序号")
    private Integer sortOrder;
}
