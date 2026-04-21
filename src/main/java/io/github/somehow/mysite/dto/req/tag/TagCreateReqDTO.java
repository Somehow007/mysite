package io.github.somehow.mysite.dto.req.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建标签请求")
public class TagCreateReqDTO {

    @NotBlank(message = "标签名称不能为空")
    @Schema(description = "标签名称", example = "Java", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "标签别名不能为空")
    @Schema(description = "URL友好别名", example = "java", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;
}
