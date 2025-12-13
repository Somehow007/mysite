package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * 文章数据控制层
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "文章数据管理")
public class ArticleController {

    private final ArticleService articleService;

    @Operation(summary = "创建文章")
    @PostMapping("/api/article/create")
    public Result<Void> createArticle(@RequestBody ArticleCreateReqDTO requestParam) {
        articleService.createArticle(requestParam);
        return Results.success();
    }

    @Operation(summary = "更新文章")
    @PutMapping("/api/article/update")
    public Result<Void> updateArticle(@RequestBody ArticleUpdateReqDTO requestParam) {
        articleService.updateArticle(requestParam);
        return Results.success();
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/api/article/delete/{id}")
    public Result<Void> deleteArticle(@PathVariable("id") String id) {
        articleService.deleteArticle(Long.parseLong(id));
        return Results.success();
    }

    @Operation(summary = "分页搜索文章信息")
    @GetMapping("/api/article/search")
    public Result<IPage<ArticlePageQueryRespDTO>> pageQueryArticle(ArticlePageQueryReqDTO requestParam) {
        return Results.success(articleService.pageQueryArticle(requestParam));
    }

    @Operation(summary = "分页获取收藏的文章")
    @GetMapping("/api/article/favorite/page")
    public Result<IPage<ArticlePageQueryRespDTO>> pageQueryFavoriteArticle(ArticleFavoritePageQueryReqDTO requestParam) {
        return Results.success(articleService.pageQueryFavoriteArticle(requestParam));
    }

    @Operation(summary = "查询单个文章信息")
    @GetMapping("/api/article/select/{id}")
    public Result<ArticleSelectRespDTO> selectArticle(@PathVariable("id") String id) {
        return Results.success(articleService.selectOneArticle(Long.parseLong(id)));
    }

    @Operation(summary = "收藏或取消收藏文章")
    @PostMapping("/api/article/favorite")
    public Result<Void> favoriteArticle(@RequestBody ArticleFavoriteReqDTO requestParam) {
        articleService.favoriteArticle(requestParam);
        return Results.success();
    }

}
