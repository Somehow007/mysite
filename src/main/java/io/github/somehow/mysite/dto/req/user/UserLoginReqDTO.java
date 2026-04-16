package io.github.somehow.mysite.dto.req.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户登录请求实体
 */
@Data
@Schema(description = "用户登录请求实体")
public class UserLoginReqDTO {

    @Schema(description = "用户名", example = "Somehow", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "密码", example = "123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
