package io.github.somehow.mysite.dto.req.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRoleUpdateReqDTO {

    @NotBlank(message = "角色不能为空")
    private String role;
}
