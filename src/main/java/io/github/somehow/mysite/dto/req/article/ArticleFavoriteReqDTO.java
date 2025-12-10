package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 收藏文章请求参数
 */
@Data
public class ArticleFavoriteReqDTO {

    /**
     * 用户id
     */
    @Schema(description = "用户id", example = "1992826310106120192", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    /**
     * 文章ID
     */
    @Schema(description = "文章ID", example = "1992838745231835136", requiredMode = Schema.RequiredMode.REQUIRED)
    private String articleId;


}
