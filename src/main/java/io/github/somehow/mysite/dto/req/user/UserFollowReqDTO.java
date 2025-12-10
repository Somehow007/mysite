package io.github.somehow.mysite.dto.req.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 关注用户请求参数
 */
@Data
@Schema(description = "关注用户请求参数")
public class UserFollowReqDTO {

    /**
     * 关注者Id
     */
    @Schema(description = "关注者Id", example = "1992271615845867520")
    private String followerId;

    /**
     * 被关注者Id
     */
    @Schema(description = "被关注者Id", example = "1992826310106120192")
    private String followeeId;
}
