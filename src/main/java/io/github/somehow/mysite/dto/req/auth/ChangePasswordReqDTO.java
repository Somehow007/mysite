package io.github.somehow.mysite.dto.req.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "修改密码请求")
public class ChangePasswordReqDTO {

    @NotBlank(message = "旧密码不能为空")
    @Schema(description = "旧密码", example = "oldPassword123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Schema(description = "新密码", example = "newPassword456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
