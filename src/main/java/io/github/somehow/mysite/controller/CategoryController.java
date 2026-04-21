package io.github.somehow.mysite.controller;

import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.category.CategoryCreateReqDTO;
import io.github.somehow.mysite.dto.req.category.CategoryUpdateReqDTO;
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

    @Operation(summary = "根据slug获取分类详情")
    @GetMapping("/v1/categories/{slug}")
    public Result<CategoryRespDTO> getCategoryBySlug(@PathVariable String slug) {
        return Results.success(categoryService.getCategoryBySlug(slug));
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
}
