package io.github.somehow.mysite.service;

import io.github.somehow.mysite.dto.req.category.CategoryCreateReqDTO;
import io.github.somehow.mysite.dto.req.category.CategoryUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.category.CategoryRespDTO;

import java.util.List;

public interface CategoryService {

    void createCategory(CategoryCreateReqDTO requestParam);

    void updateCategory(Long id, CategoryUpdateReqDTO requestParam);

    void deleteCategory(Long id);

    List<CategoryRespDTO> listCategories();

    CategoryRespDTO getCategoryBySlug(String slug);
}
