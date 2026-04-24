package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.CategoryDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dto.req.category.*;
import io.github.somehow.mysite.dto.resp.category.CategoryRespDTO;
import io.github.somehow.mysite.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    private static final String CACHE_NAME = "categories";
    private static final String CACHE_TREE_NAME = "category_tree";

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_NAME, CACHE_TREE_NAME}, allEntries = true)
    public void createCategory(CategoryCreateReqDTO requestParam) {
        if (requestParam.getParentId() != null) {
            CategoryDO parent = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                    .eq(CategoryDO::getId, requestParam.getParentId())
                    .eq(CategoryDO::getDelFlag, 0));
            if (Objects.isNull(parent)) {
                throw new ClientException("父分类不存在");
            }
            if (parent.getLevel() >= 3) {
                throw new ClientException("分类层级不能超过三级");
            }
        }

        CategoryDO categoryDO = BeanUtil.toBean(requestParam, CategoryDO.class);
        categoryDO.setId(IdUtil.getSnowflakeNextId());
        
        if (categoryDO.getLevel() == null) {
            categoryDO.setLevel(requestParam.getParentId() == null ? 1 : 2);
        }
        
        if (categoryDO.getStatus() == null) {
            categoryDO.setStatus(1);
        }
        
        if (categoryDO.getSortOrder() == null) {
            categoryDO.setSortOrder(0);
        }

        try {
            categoryMapper.insert(categoryDO);
            
            String path = buildCategoryPath(categoryDO.getId(), categoryDO.getParentId());
            categoryDO.setPath(path);
            categoryMapper.updateById(categoryDO);
        } catch (DuplicateKeyException e) {
            throw new ClientException("分类别名已存在: " + requestParam.getSlug());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_NAME, CACHE_TREE_NAME}, allEntries = true)
    public void updateCategory(Long id, CategoryUpdateReqDTO requestParam) {
        CategoryDO existing = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getId, id)
                .eq(CategoryDO::getDelFlag, 0));
        if (Objects.isNull(existing)) {
            throw new ClientException("分类不存在");
        }

        if (requestParam.getParentId() != null && !requestParam.getParentId().equals(existing.getParentId())) {
            if (requestParam.getParentId().equals(id)) {
                throw new ClientException("不能将自己设置为父分类");
            }
            
            CategoryDO parent = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                    .eq(CategoryDO::getId, requestParam.getParentId())
                    .eq(CategoryDO::getDelFlag, 0));
            if (Objects.isNull(parent)) {
                throw new ClientException("父分类不存在");
            }
            
            if (parent.getLevel() >= 3) {
                throw new ClientException("分类层级不能超过三级");
            }
            
            if (hasChildren(id)) {
                throw new ClientException("该分类下有子分类，不能修改父分类");
            }
            
            existing.setParentId(requestParam.getParentId());
            existing.setLevel(parent.getLevel() + 1);
            existing.setPath(buildCategoryPath(id, requestParam.getParentId()));
        }

        if (requestParam.getName() != null) existing.setName(requestParam.getName());
        if (requestParam.getSlug() != null) existing.setSlug(requestParam.getSlug());
        if (requestParam.getDescription() != null) existing.setDescription(requestParam.getDescription());
        if (requestParam.getSortOrder() != null) existing.setSortOrder(requestParam.getSortOrder());
        if (requestParam.getLevel() != null) existing.setLevel(requestParam.getLevel());
        if (requestParam.getStatus() != null) existing.setStatus(requestParam.getStatus());
        if (requestParam.getIcon() != null) existing.setIcon(requestParam.getIcon());
        if (requestParam.getColor() != null) existing.setColor(requestParam.getColor());
        if (requestParam.getSeoTitle() != null) existing.setSeoTitle(requestParam.getSeoTitle());
        if (requestParam.getSeoDescription() != null) existing.setSeoDescription(requestParam.getSeoDescription());
        if (requestParam.getSeoKeywords() != null) existing.setSeoKeywords(requestParam.getSeoKeywords());

        try {
            categoryMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new ClientException("分类别名已存在: " + requestParam.getSlug());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_NAME, CACHE_TREE_NAME}, allEntries = true)
    public void deleteCategory(Long id) {
        CategoryDO category = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getId, id)
                .eq(CategoryDO::getDelFlag, 0));
        if (Objects.isNull(category)) {
            throw new ClientException("分类不存在");
        }

        if (hasChildren(id)) {
            throw new ClientException("该分类下有子分类，无法删除");
        }

        Long articleCount = articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getCategoryId, id)
                .eq(ArticleDO::getDelFlag, 0));
        if (articleCount > 0) {
            throw new ClientException("该分类下还有文章，无法删除");
        }

        CategoryDO categoryDO = new CategoryDO();
        categoryDO.setId(id);
        categoryDO.setDelFlag(1);
        categoryMapper.updateById(categoryDO);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'all'")
    public List<CategoryRespDTO> listCategories() {
        List<CategoryDO> categories = categoryMapper.selectList(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getDelFlag, 0)
                .orderByAsc(CategoryDO::getSortOrder)
                .orderByDesc(CategoryDO::getCreateTime));

        return categories.stream().map(cat -> {
            CategoryRespDTO dto = BeanUtil.toBean(cat, CategoryRespDTO.class);
            dto.setArticleCount(articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                    .eq(ArticleDO::getCategoryId, cat.getId())
                    .eq(ArticleDO::getDelFlag, 0)));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'slug:' + #slug")
    public CategoryRespDTO getCategoryBySlug(String slug) {
        CategoryDO categoryDO = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getSlug, slug)
                .eq(CategoryDO::getDelFlag, 0));
        if (Objects.isNull(categoryDO)) {
            throw new ClientException("分类不存在: " + slug);
        }
        CategoryRespDTO dto = BeanUtil.toBean(categoryDO, CategoryRespDTO.class);
        dto.setArticleCount(articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getCategoryId, categoryDO.getId())
                .eq(ArticleDO::getDelFlag, 0)));
        return dto;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'id:' + #id")
    public CategoryRespDTO getCategoryById(Long id) {
        CategoryDO categoryDO = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getId, id)
                .eq(CategoryDO::getDelFlag, 0));
        if (Objects.isNull(categoryDO)) {
            throw new ClientException("分类不存在");
        }
        CategoryRespDTO dto = BeanUtil.toBean(categoryDO, CategoryRespDTO.class);
        dto.setArticleCount(articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getCategoryId, categoryDO.getId())
                .eq(ArticleDO::getDelFlag, 0)));
        return dto;
    }

    @Override
    public List<CategoryRespDTO> queryCategories(CategoryQueryReqDTO requestParam) {
        LambdaQueryWrapper<CategoryDO> wrapper = Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getDelFlag, 0);

        if (StrUtil.isNotBlank(requestParam.getName())) {
            wrapper.like(CategoryDO::getName, requestParam.getName());
        }
        if (requestParam.getParentId() != null) {
            wrapper.eq(CategoryDO::getParentId, requestParam.getParentId());
        }
        if (requestParam.getLevel() != null) {
            wrapper.eq(CategoryDO::getLevel, requestParam.getLevel());
        }
        if (requestParam.getStatus() != null) {
            wrapper.eq(CategoryDO::getStatus, requestParam.getStatus());
        }

        wrapper.orderByAsc(CategoryDO::getSortOrder)
               .orderByDesc(CategoryDO::getCreateTime);

        List<CategoryDO> categories = categoryMapper.selectList(wrapper);

        return categories.stream().map(cat -> {
            CategoryRespDTO dto = BeanUtil.toBean(cat, CategoryRespDTO.class);
            dto.setArticleCount(articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                    .eq(ArticleDO::getCategoryId, cat.getId())
                    .eq(ArticleDO::getDelFlag, 0)));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_NAME, CACHE_TREE_NAME}, allEntries = true)
    public void updateCategoryStatus(Long id, Integer status) {
        CategoryDO category = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getId, id)
                .eq(CategoryDO::getDelFlag, 0));
        if (Objects.isNull(category)) {
            throw new ClientException("分类不存在");
        }

        CategoryDO updateDO = new CategoryDO();
        updateDO.setId(id);
        updateDO.setStatus(status);
        categoryMapper.updateById(updateDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_NAME, CACHE_TREE_NAME}, allEntries = true)
    public void batchUpdateStatus(CategoryBatchStatusReqDTO requestParam) {
        for (Long id : requestParam.getIds()) {
            updateCategoryStatus(id, requestParam.getStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_NAME, CACHE_TREE_NAME}, allEntries = true)
    public void batchDelete(CategoryBatchDeleteReqDTO requestParam) {
        for (Long id : requestParam.getIds()) {
            deleteCategory(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_NAME, CACHE_TREE_NAME}, allEntries = true)
    public void updateSortOrder(CategorySortReqDTO requestParam) {
        CategoryDO category = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getId, requestParam.getId())
                .eq(CategoryDO::getDelFlag, 0));
        if (Objects.isNull(category)) {
            throw new ClientException("分类不存在");
        }

        CategoryDO updateDO = new CategoryDO();
        updateDO.setId(requestParam.getId());
        updateDO.setSortOrder(requestParam.getSortOrder());
        categoryMapper.updateById(updateDO);
    }

    @Override
    @Cacheable(value = CACHE_TREE_NAME, key = "'tree'")
    public List<CategoryRespDTO> getCategoryTree() {
        List<CategoryRespDTO> allCategories = listCategories();
        return buildCategoryTree(allCategories, null);
    }

    @Override
    public List<CategoryRespDTO> getChildrenByParentId(Long parentId) {
        List<CategoryDO> categories = categoryMapper.selectList(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getParentId, parentId)
                .eq(CategoryDO::getDelFlag, 0)
                .orderByAsc(CategoryDO::getSortOrder)
                .orderByDesc(CategoryDO::getCreateTime));

        return categories.stream().map(cat -> {
            CategoryRespDTO dto = BeanUtil.toBean(cat, CategoryRespDTO.class);
            dto.setArticleCount(articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                    .eq(ArticleDO::getCategoryId, cat.getId())
                    .eq(ArticleDO::getDelFlag, 0)));
            return dto;
        }).collect(Collectors.toList());
    }

    private String buildCategoryPath(Long categoryId, Long parentId) {
        if (parentId == null) {
            return String.valueOf(categoryId);
        }
        
        CategoryDO parent = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getId, parentId)
                .eq(CategoryDO::getDelFlag, 0));
        
        if (parent == null) {
            return String.valueOf(categoryId);
        }
        
        return parent.getPath() + "," + categoryId;
    }

    private boolean hasChildren(Long categoryId) {
        Long count = categoryMapper.selectCount(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getParentId, categoryId)
                .eq(CategoryDO::getDelFlag, 0));
        return count > 0;
    }

    private List<CategoryRespDTO> buildCategoryTree(List<CategoryRespDTO> allCategories, Long parentId) {
        List<CategoryRespDTO> tree = new ArrayList<>();
        
        for (CategoryRespDTO category : allCategories) {
            boolean isParentMatch = (parentId == null && category.getParentId() == null) ||
                                   (parentId != null && parentId.equals(category.getParentId()));
            
            if (isParentMatch) {
                List<CategoryRespDTO> children = buildCategoryTree(allCategories, category.getId());
                category.setChildren(children.isEmpty() ? null : children);
                tree.add(category);
            }
        }
        
        return tree;
    }
}
