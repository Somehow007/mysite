package io.github.somehow.mysite.dto.req.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CollectionUpdateReqDTO {

    @Schema(description = "合集标题")
    private String title;

    @Schema(description = "合集描述")
    private String description;

    @Schema(description = "合集封面图片URL")
    private String coverImage;

    @Schema(description = "排序序号")
    private Integer sortOrder;
}
