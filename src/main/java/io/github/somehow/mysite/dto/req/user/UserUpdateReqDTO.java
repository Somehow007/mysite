package io.github.somehow.mysite.dto.req.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新用户信息请求实体
 */
@Data
@Schema(description = "更新用户信息请求实体")
public class UserUpdateReqDTO {

    /**
     * 用户id
     */
    @Schema(description = "用户id", example = "1992826310106120192", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    /**
     * 昵称
     */
    @Schema(description = "昵称", example = "Somehow", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    /**
     * 姓名
     */
    @Schema(description = "姓名", example = "Some how", requiredMode = Schema.RequiredMode.REQUIRED)
    private String realName;

    /**
     * 性别 0: 男性 1: 女性 2: 保密
     */
    @Schema(description = "性别 0: 男性 1: 女性 2: 保密", example = "0", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer sex;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱", example = "test@163.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    /**
     * 手机号
     */
    @Schema(description = "手机号", example = "19732572071", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNumber;

    @Schema(description = "头像URL")
    private String avatar;
}
