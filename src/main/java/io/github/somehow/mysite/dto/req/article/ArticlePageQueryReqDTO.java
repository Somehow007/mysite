package io.github.somehow.mysite.dto.req.article;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页查询文章请求体
 */
@Data
public class ArticlePageQueryReqDTO extends Page {

    /**
     * 文章标题
     */
    @Schema(description = "文章标题", example = "如何一夜暴富")
    private String title;

    /**
     * 作者名称
     */
    @Schema(description = "作者名称", example = "Somehow007")
    private String authorName;

}
