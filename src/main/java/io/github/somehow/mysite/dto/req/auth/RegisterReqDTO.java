package io.github.somehow.mysite.dto.req.auth;

import io.github.somehow.mysite.commons.framework.validation.ValidEmail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterReqDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "somehow", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "真实姓名", example = "Some How", requiredMode = Schema.RequiredMode.REQUIRED)
    private String realName;

    @Schema(description = "性别 0:男 1:女 2:保密", example = "0")
    private Integer sex;

    @NotBlank(message = "邮箱不能为空")
    @ValidEmail
    @Schema(description = "邮箱", example = "test@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNumber;
}
