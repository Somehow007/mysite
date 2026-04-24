package io.github.somehow.mysite.dto.req.category;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "分类查询请求")
public class CategoryQueryReqDTO {

    @Schema(description = "分类名称（模糊查询）")
    private String name;

    @Schema(description = "父分类ID")
    private Long parentId;

    @Schema(description = "分类层级")
    private Integer level;

    @Schema(description = "状态 0:禁用 1:启用")
    private Integer status;

    @Schema(description = "是否返回树形结构", example = "false")
    private Boolean tree;

    @Schema(description = "页码", example = "1")
    private Integer current = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer size = 10;
}
