package io.github.somehow.mysite.dto.req.category;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新分类请求")
public class CategoryUpdateReqDTO {

    private String name;
    private String slug;
    private String description;
    private Integer sortOrder;
}
