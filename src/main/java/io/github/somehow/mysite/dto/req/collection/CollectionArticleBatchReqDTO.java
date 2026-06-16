package io.github.somehow.mysite.dto.req.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class CollectionArticleBatchReqDTO {

    @Schema(description = "文章ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> articleIds;
}
