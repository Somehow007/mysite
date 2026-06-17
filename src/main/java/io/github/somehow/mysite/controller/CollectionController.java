package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleBatchReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleSortReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionCreateReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.collection.ArticleNavInfoRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionDetailRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionPageQueryRespDTO;
import io.github.somehow.mysite.service.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "合集数据管理")
public class CollectionController {

    private final CollectionService collectionService;

    @Operation(summary = "创建合集")
    @PostMapping("/v1/collections")
    public Result<Long> createCollection(@Valid @RequestBody CollectionCreateReqDTO requestParam) {
        return Results.success(collectionService.createCollection(requestParam));
    }

    @Operation(summary = "更新合集")
    @PutMapping("/v1/collections/{id}")
    public Result<Void> updateCollection(@PathVariable Long id, @Valid @RequestBody CollectionUpdateReqDTO requestParam) {
        collectionService.updateCollection(id, requestParam);
        return Results.success();
    }

    @Operation(summary = "删除合集")
    @DeleteMapping("/v1/collections/{id}")
    public Result<Void> deleteCollection(@PathVariable Long id) {
        collectionService.deleteCollection(id);
        return Results.success();
    }

    @Operation(summary = "分页查询合集")
    @GetMapping("/v1/collections")
    public Result<IPage<CollectionPageQueryRespDTO>> pageQueryCollection(CollectionPageQueryReqDTO requestParam) {
        return Results.success(collectionService.pageQueryCollection(requestParam));
    }

    @Operation(summary = "查询合集详情（含文章列表）")
    @GetMapping("/v1/collections/{id}")
    public Result<CollectionDetailRespDTO> getCollectionDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return Results.success(collectionService.getCollectionDetail(id, current, size));
    }

    @Operation(summary = "添加文章到合集")
    @PostMapping("/v1/collections/{collectionId}/articles/{articleId}")
    public Result<Void> addArticleToCollection(@PathVariable Long collectionId, @PathVariable Long articleId) {
        collectionService.addArticleToCollection(collectionId, articleId);
        return Results.success();
    }

    @Operation(summary = "从合集中移除文章")
    @DeleteMapping("/v1/collections/{collectionId}/articles/{articleId}")
    public Result<Void> removeArticleFromCollection(@PathVariable Long collectionId, @PathVariable Long articleId) {
        collectionService.removeArticleFromCollection(collectionId, articleId);
        return Results.success();
    }

    @Operation(summary = "批量添加文章到合集")
    @PostMapping("/v1/collections/{collectionId}/articles/batch")
    public Result<Void> batchAddArticles(@PathVariable Long collectionId, @Valid @RequestBody CollectionArticleBatchReqDTO requestParam) {
        collectionService.batchAddArticles(collectionId, requestParam);
        return Results.success();
    }

    @Operation(summary = "调整合集中文章排序")
    @PutMapping("/v1/collections/{collectionId}/articles/sort")
    public Result<Void> updateArticleSort(@PathVariable Long collectionId, @Valid @RequestBody CollectionArticleSortReqDTO requestParam) {
        collectionService.updateArticleSort(collectionId, requestParam);
        return Results.success();
    }

    @Operation(summary = "获取文章导航信息（上一篇/下一篇）")
    @GetMapping("/v1/articles/{articleId}/navigation")
    public Result<ArticleNavInfoRespDTO> getArticleNavigation(@PathVariable Long articleId) {
        return Results.success(collectionService.getArticleNavigation(articleId));
    }
}
