package io.github.somehow.mysite.dto.req.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class CollectionArticleSortReqDTO {

    @Schema(description = "按新顺序排列的文章ID数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> articleIds;
}
