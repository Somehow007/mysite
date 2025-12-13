package io.github.somehow.mysite.dto.req.article;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询文章请求参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "搜索文章请求参数")
public class ArticlePageQueryReqDTO extends Page {

    /**
     * 搜索关键词
     */
    @Schema(description = "搜索关键词", example = "如何")
    private String keyword;

    /**
     * 搜索类型
     */
    @Schema(description = "搜索类型: title-按标题搜索, content-按内容搜索, author-按作者搜索", example = "title")
    private String searchType;
}