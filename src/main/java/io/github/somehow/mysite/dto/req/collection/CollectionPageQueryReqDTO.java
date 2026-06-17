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
}
