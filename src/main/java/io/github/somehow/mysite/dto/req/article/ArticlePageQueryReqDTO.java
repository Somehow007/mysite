package io.github.somehow.mysite.dto.req.article;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "搜索文章请求参数")
public class ArticlePageQueryReqDTO extends Page {

    @Schema(description = "搜索关键词", example = "如何")
    private String keyword;

    @Schema(description = "搜索类型: title-按标题搜索, content-按内容搜索, author-按作者搜索", example = "title")
    private String searchType;

    @Schema(description = "分类slug，用于按分类过滤文章")
    private String categorySlug;

    @Schema(description = "标签slug，用于按标签过滤文章")
    private String tagSlug;

    @Schema(description = "发布状态: 1-已发布, 0-草稿, null-全部")
    private Integer published;

    @Schema(description = "排序字段: createTime-创建时间, viewCount-浏览量", example = "createTime")
    private String sortField;

    @Schema(description = "排序方向: asc-升序, desc-降序", example = "desc")
    private String sortOrder;
}
