package io.github.somehow.mysite.dto.req.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建评论请求参数
 */
@Data
@Schema(description = "创建评论请求参数")
public class CommentCreateReqDTO {

    /**
     * 评论内容
     */
    @Schema(description = "评论内容", example = "我悟了", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    /**
     * 评论人ID
     */
    @Schema(description = "评论人ID", example = "1992826310106120192", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    /**
     * 父评论Id，没有默认为 null
     */
    @Schema(description = "父评论Id，没有默认为 null", example = "", requiredMode = Schema.RequiredMode.REQUIRED)
    private String parentId;

    /**
     * 评论的文章Id
     */
    @Schema(description = "评论的文章Id", example = "1992838745231835136", requiredMode = Schema.RequiredMode.REQUIRED)
    private String articleId;
}
