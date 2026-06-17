package io.github.somehow.mysite.dto.req.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CollectionArticleSortReqDTO {

    @Schema(description = "按新顺序排列的文章ID数组", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "文章ID列表不能为空")
    private List<Long> articleIds;
}
