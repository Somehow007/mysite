package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ArticleFavoriteReqDTO {

    @Schema(hidden = true)
    private String userId;

    @Schema(description = "文章ID", example = "1992838745231835136", requiredMode = Schema.RequiredMode.REQUIRED)
    private String articleId;
}
