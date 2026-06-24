package io.github.somehow.mysite.dto.req.collection;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "合集分页查询请求参数")
public class CollectionPageQueryReqDTO extends Page {

    @Schema(description = "搜索关键词")
    private String keyword;

    @Schema(description = "按作者筛选")
    private Long authorId;

    @Schema(description = "排序方式：viewCount 表示按文章总浏览量降序，留空则按 sort_order、创建时间排序")
    private String sortBy;

    @Schema(description = "排序字段: createTime-创建时间, title-标题, articleCount-文章数", example = "createTime")
    private String sortField;

    @Schema(description = "排序方向: asc-升序, desc-降序", example = "desc")
    private String sortOrder;
}
