package io.github.somehow.mysite.dto.req.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusUpdateReqDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
