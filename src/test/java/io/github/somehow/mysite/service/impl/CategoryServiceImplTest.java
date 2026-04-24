package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.CategoryDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dto.req.category.*;
import io.github.somehow.mysite.dto.resp.category.CategoryRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ArticleMapper articleMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private CategoryDO testCategory;
    private CategoryCreateReqDTO createReqDTO;

    @BeforeEach
    void setUp() {
        testCategory = CategoryDO.builder()
                .id(1L)
                .name("测试分类")
                .slug("test-category")
                .description("测试分类描述")
                .sortOrder(0)
                .parentId(null)
                .level(1)
                .path("1")
                .status(1)
                .build();

        createReqDTO = new CategoryCreateReqDTO();
        createReqDTO.setName("测试分类");
        createReqDTO.setSlug("test-category");
        createReqDTO.setDescription("测试分类描述");
        createReqDTO.setSortOrder(0);
    }

    @Test
    void testCreateCategory_Success() {
        when(categoryMapper.insert(any(CategoryDO.class))).thenReturn(1);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.createCategory(createReqDTO));

        verify(categoryMapper, times(1)).insert(any(CategoryDO.class));
        verify(categoryMapper, times(1)).updateById(any(CategoryDO.class));
    }

    @Test
    void testCreateCategory_WithParent_Success() {
        CategoryDO parentCategory = CategoryDO.builder()
                .id(2L)
                .name("父分类")
                .slug("parent-category")
                .level(1)
                .build();

        createReqDTO.setParentId(2L);

        when(categoryMapper.selectOne(any())).thenReturn(parentCategory);
        when(categoryMapper.insert(any(CategoryDO.class))).thenReturn(1);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.createCategory(createReqDTO));

        verify(categoryMapper, times(1)).selectOne(any());
    }

    @Test
    void testCreateCategory_ParentNotExist_ThrowsException() {
        createReqDTO.setParentId(999L);

        when(categoryMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> categoryService.createCategory(createReqDTO));
    }

    @Test
    void testCreateCategory_ParentLevelTooDeep_ThrowsException() {
        CategoryDO parentCategory = CategoryDO.builder()
                .id(2L)
                .name("父分类")
                .slug("parent-category")
                .level(3)
                .build();

        createReqDTO.setParentId(2L);

        when(categoryMapper.selectOne(any())).thenReturn(parentCategory);

        assertThrows(ClientException.class, () -> categoryService.createCategory(createReqDTO));
    }

    @Test
    void testCreateCategory_DuplicateSlug_ThrowsException() {
        when(categoryMapper.insert(any(CategoryDO.class))).thenThrow(DuplicateKeyException.class);

        assertThrows(ClientException.class, () -> categoryService.createCategory(createReqDTO));
    }

    @Test
    void testUpdateCategory_Success() {
        CategoryUpdateReqDTO updateReqDTO = new CategoryUpdateReqDTO();
        updateReqDTO.setName("更新后的分类");
        updateReqDTO.setSlug("updated-category");

        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.updateCategory(1L, updateReqDTO));

        verify(categoryMapper, times(1)).updateById(any(CategoryDO.class));
    }

    @Test
    void testUpdateCategory_NotExist_ThrowsException() {
        when(categoryMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> categoryService.updateCategory(999L, new CategoryUpdateReqDTO()));
    }

    @Test
    void testDeleteCategory_Success() {
        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(articleMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.deleteCategory(1L));

        verify(categoryMapper, times(1)).updateById(any(CategoryDO.class));
    }

    @Test
    void testDeleteCategory_HasArticles_ThrowsException() {
        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(articleMapper.selectCount(any())).thenReturn(5L);

        assertThrows(ClientException.class, () -> categoryService.deleteCategory(1L));
    }

    @Test
    void testDeleteCategory_HasChildren_ThrowsException() {
        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(articleMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        assertThrows(ClientException.class, () -> categoryService.deleteCategory(1L));
    }

    @Test
    void testListCategories_Success() {
        List<CategoryDO> categories = Arrays.asList(testCategory);

        when(categoryMapper.selectList(any())).thenReturn(categories);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        List<CategoryRespDTO> result = categoryService.listCategories();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试分类", result.get(0).getName());
    }

    @Test
    void testGetCategoryBySlug_Success() {
        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        CategoryRespDTO result = categoryService.getCategoryBySlug("test-category");

        assertNotNull(result);
        assertEquals("测试分类", result.getName());
    }

    @Test
    void testGetCategoryBySlug_NotExist_ThrowsException() {
        when(categoryMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> categoryService.getCategoryBySlug("not-exist"));
    }

    @Test
    void testGetCategoryById_Success() {
        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        CategoryRespDTO result = categoryService.getCategoryById(1L);

        assertNotNull(result);
        assertEquals("测试分类", result.getName());
    }

    @Test
    void testQueryCategories_Success() {
        CategoryQueryReqDTO queryReqDTO = new CategoryQueryReqDTO();
        queryReqDTO.setName("测试");
        queryReqDTO.setStatus(1);

        List<CategoryDO> categories = Arrays.asList(testCategory);

        when(categoryMapper.selectList(any())).thenReturn(categories);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        List<CategoryRespDTO> result = categoryService.queryCategories(queryReqDTO);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testUpdateCategoryStatus_Success() {
        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.updateCategoryStatus(1L, 0));

        verify(categoryMapper, times(1)).updateById(any(CategoryDO.class));
    }

    @Test
    void testBatchUpdateStatus_Success() {
        CategoryBatchStatusReqDTO batchReqDTO = new CategoryBatchStatusReqDTO();
        batchReqDTO.setIds(Arrays.asList(1L, 2L));
        batchReqDTO.setStatus(0);

        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.batchUpdateStatus(batchReqDTO));

        verify(categoryMapper, times(2)).updateById(any(CategoryDO.class));
    }

    @Test
    void testBatchDelete_Success() {
        CategoryBatchDeleteReqDTO batchReqDTO = new CategoryBatchDeleteReqDTO();
        batchReqDTO.setIds(Arrays.asList(1L, 2L));

        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(articleMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.batchDelete(batchReqDTO));

        verify(categoryMapper, times(2)).updateById(any(CategoryDO.class));
    }

    @Test
    void testUpdateSortOrder_Success() {
        CategorySortReqDTO sortReqDTO = new CategorySortReqDTO();
        sortReqDTO.setId(1L);
        sortReqDTO.setSortOrder(10);

        when(categoryMapper.selectOne(any())).thenReturn(testCategory);
        when(categoryMapper.updateById(any(CategoryDO.class))).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.updateSortOrder(sortReqDTO));

        verify(categoryMapper, times(1)).updateById(any(CategoryDO.class));
    }

    @Test
    void testGetCategoryTree_Success() {
        CategoryDO childCategory = CategoryDO.builder()
                .id(2L)
                .name("子分类")
                .slug("child-category")
                .parentId(1L)
                .level(2)
                .build();

        List<CategoryDO> categories = Arrays.asList(testCategory, childCategory);

        when(categoryMapper.selectList(any())).thenReturn(categories);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        List<CategoryRespDTO> result = categoryService.getCategoryTree();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getChildren());
        assertEquals(1, result.get(0).getChildren().size());
    }

    @Test
    void testGetChildrenByParentId_Success() {
        CategoryDO childCategory = CategoryDO.builder()
                .id(2L)
                .name("子分类")
                .slug("child-category")
                .parentId(1L)
                .level(2)
                .build();

        List<CategoryDO> children = Arrays.asList(childCategory);

        when(categoryMapper.selectList(any())).thenReturn(children);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        List<CategoryRespDTO> result = categoryService.getChildrenByParentId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("子分类", result.get(0).getName());
    }
}
