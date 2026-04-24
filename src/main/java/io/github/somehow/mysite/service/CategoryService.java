package io.github.somehow.mysite.service;

import io.github.somehow.mysite.dto.req.category.*;
import io.github.somehow.mysite.dto.resp.category.CategoryRespDTO;

import java.util.List;

public interface CategoryService {

    void createCategory(CategoryCreateReqDTO requestParam);

    void updateCategory(Long id, CategoryUpdateReqDTO requestParam);

    void deleteCategory(Long id);

    List<CategoryRespDTO> listCategories();

    CategoryRespDTO getCategoryBySlug(String slug);

    CategoryRespDTO getCategoryById(Long id);

    List<CategoryRespDTO> queryCategories(CategoryQueryReqDTO requestParam);

    void updateCategoryStatus(Long id, Integer status);

    void batchUpdateStatus(CategoryBatchStatusReqDTO requestParam);

    void batchDelete(CategoryBatchDeleteReqDTO requestParam);

    void updateSortOrder(CategorySortReqDTO requestParam);

    List<CategoryRespDTO> getCategoryTree();

    List<CategoryRespDTO> getChildrenByParentId(Long parentId);
}
