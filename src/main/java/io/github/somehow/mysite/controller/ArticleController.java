package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArchiveRespDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "文章数据管理")
public class ArticleController {

    private final ArticleService articleService;

    @Operation(summary = "创建文章")
    @PostMapping("/v1/articles")
    public Result<Void> createArticle(@RequestBody ArticleCreateReqDTO requestParam) {
        articleService.createArticle(requestParam);
        return Results.success();
    }

    @Operation(summary = "更新文章")
    @PutMapping("/v1/articles/{id}")
    public Result<Void> updateArticle(@PathVariable Long id, @RequestBody ArticleUpdateReqDTO requestParam) {
        requestParam.setId(id);
        articleService.updateArticle(requestParam);
        return Results.success();
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/v1/articles/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Results.success();
    }

    @Operation(summary = "分页搜索文章信息")
    @GetMapping("/v1/articles")
    public Result<IPage<ArticlePageQueryRespDTO>> pageQueryArticle(ArticlePageQueryReqDTO requestParam) {
        return Results.success(articleService.pageQueryArticle(requestParam));
    }

    @Operation(summary = "分页获取收藏的文章")
    @GetMapping("/v1/articles/favorites")
    public Result<IPage<ArticlePageQueryRespDTO>> pageQueryFavoriteArticle(ArticleFavoritePageQueryReqDTO requestParam) {
        return Results.success(articleService.pageQueryFavoriteArticle(requestParam));
    }

    @Operation(summary = "查询单个文章信息")
    @GetMapping("/v1/articles/{id}")
    public Result<ArticleSelectRespDTO> selectArticle(@PathVariable Long id) {
        return Results.success(articleService.selectOneArticle(id));
    }

    @Operation(summary = "收藏或取消收藏文章")
    @PostMapping("/v1/articles/{id}/favorite")
    public Result<Void> favoriteArticle(@PathVariable Long id, @RequestBody ArticleFavoriteReqDTO requestParam) {
        requestParam.setArticleId(id.toString());
        articleService.favoriteArticle(requestParam);
        return Results.success();
    }

    @Operation(summary = "归档列表（按年月分组）")
    @GetMapping("/v1/articles/archive")
    public Result<List<ArchiveRespDTO>> getArchive() {
        return Results.success(articleService.getArchive());
    }
}
