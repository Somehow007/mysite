package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文章置顶请求实体")
public class ArticleTopReqDTO {

    @Schema(description = "置顶状态: 0-取消置顶, 1-置顶", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer isTop;
}
