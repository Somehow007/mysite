package io.github.somehow.mysite.dto.req.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CollectionArticleBatchReqDTO {

    @Schema(description = "文章ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "文章ID列表不能为空")
    private List<Long> articleIds;
}
