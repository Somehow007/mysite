package io.github.somehow.mysite.dto.req.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CommentCreateReqDTO {

    @Schema(description = "文章ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long articleId;

    @Schema(description = "父评论ID（回复时传入）")
    private Long parentId;

    @Schema(description = "昵称（游客必填，登录用户自动填充）")
    private String nickname;

    @Schema(description = "邮箱（用于Gravatar头像）")
    private String email;

    @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
