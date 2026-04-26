package io.github.somehow.mysite.dto.req.user;

import io.github.somehow.mysite.commons.framework.validation.ValidEmail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "用户注册请求实体")
public class UserRegistryReqDTO {

    @NotBlank(message = "昵称不能为空")
    @Schema(description = "昵称", example = "Somehow", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Schema(description = "姓名", example = "Some how", requiredMode = Schema.RequiredMode.REQUIRED)
    private String realName;

    @Schema(description = "性别 0: 男性 1: 女性 2: 保密", example = "0", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer sex;

    @NotBlank(message = "邮箱不能为空")
    @ValidEmail
    @Schema(description = "邮箱", example = "test@163.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号", example = "19732572071", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNumber;
}
