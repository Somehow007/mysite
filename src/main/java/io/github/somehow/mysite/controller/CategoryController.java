package io.github.somehow.mysite.controller;

import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.category.*;
import io.github.somehow.mysite.dto.resp.category.CategoryRespDTO;
import io.github.somehow.mysite.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "分类管理")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "获取所有分类")
    @GetMapping("/v1/categories")
    public Result<List<CategoryRespDTO>> listCategories() {
        return Results.success(categoryService.listCategories());
    }

    @Operation(summary = "获取分类树形结构")
    @GetMapping("/v1/categories/tree")
    public Result<List<CategoryRespDTO>> getCategoryTree() {
        return Results.success(categoryService.getCategoryTree());
    }

    @Operation(summary = "查询分类（支持多条件筛选）")
    @GetMapping("/v1/categories/query")
    public Result<List<CategoryRespDTO>> queryCategories(CategoryQueryReqDTO requestParam) {
        return Results.success(categoryService.queryCategories(requestParam));
    }

    @Operation(summary = "根据ID获取分类详情")
    @GetMapping("/v1/categories/id/{id}")
    public Result<CategoryRespDTO> getCategoryById(@PathVariable Long id) {
        return Results.success(categoryService.getCategoryById(id));
    }

    @Operation(summary = "根据slug获取分类详情")
    @GetMapping("/v1/categories/{slug}")
    public Result<CategoryRespDTO> getCategoryBySlug(@PathVariable String slug) {
        return Results.success(categoryService.getCategoryBySlug(slug));
    }

    @Operation(summary = "获取子分类列表")
    @GetMapping("/v1/categories/{parentId}/children")
    public Result<List<CategoryRespDTO>> getChildrenByParentId(@PathVariable Long parentId) {
        return Results.success(categoryService.getChildrenByParentId(parentId));
    }

    @Operation(summary = "创建分类")
    @PostMapping("/v1/categories")
    public Result<Void> createCategory(@Valid @RequestBody CategoryCreateReqDTO requestParam) {
        categoryService.createCategory(requestParam);
        return Results.success();
    }

    @Operation(summary = "更新分类")
    @PutMapping("/v1/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody CategoryUpdateReqDTO requestParam) {
        categoryService.updateCategory(id, requestParam);
        return Results.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/v1/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Results.success();
    }

    @Operation(summary = "更新分类状态")
    @PatchMapping("/v1/categories/{id}/status")
    public Result<Void> updateCategoryStatus(@PathVariable Long id, @RequestParam Integer status) {
        categoryService.updateCategoryStatus(id, status);
        return Results.success();
    }

    @Operation(summary = "批量更新分类状态")
    @PatchMapping("/v1/categories/batch/status")
    public Result<Void> batchUpdateStatus(@Valid @RequestBody CategoryBatchStatusReqDTO requestParam) {
        categoryService.batchUpdateStatus(requestParam);
        return Results.success();
    }

    @Operation(summary = "批量删除分类")
    @DeleteMapping("/v1/categories/batch")
    public Result<Void> batchDelete(@Valid @RequestBody CategoryBatchDeleteReqDTO requestParam) {
        categoryService.batchDelete(requestParam);
        return Results.success();
    }

    @Operation(summary = "更新分类排序")
    @PatchMapping("/v1/categories/sort")
    public Result<Void> updateSortOrder(@Valid @RequestBody CategorySortReqDTO requestParam) {
        categoryService.updateSortOrder(requestParam);
        return Results.success();
    }
}
