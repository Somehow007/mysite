package io.github.somehow.mysite.dto.req.article;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量删除文章请求")
public class ArticleBatchDeleteReqDTO {

    @NotEmpty(message = "文章ID列表不能为空")
    @Schema(description = "文章ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}
