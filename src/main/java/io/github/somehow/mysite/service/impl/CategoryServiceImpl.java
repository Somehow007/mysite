package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
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
                throw new ClientException(ErrorCode.CATEGORY_PARENT_NOT_FOUND);
            }
            if (parent.getLevel() >= 3) {
                throw new ClientException(ErrorCode.CATEGORY_LEVEL_EXCEEDED);
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
            throw new ClientException(ErrorCode.CATEGORY_SLUG_EXISTS);
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
            throw new ClientException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (requestParam.getParentId() != null && !requestParam.getParentId().equals(existing.getParentId())) {
            if (requestParam.getParentId().equals(id)) {
                throw new ClientException(ErrorCode.CATEGORY_CANNOT_SET_SELF_AS_PARENT);
            }
            
            CategoryDO parent = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                    .eq(CategoryDO::getId, requestParam.getParentId())
                    .eq(CategoryDO::getDelFlag, 0));
            if (Objects.isNull(parent)) {
                throw new ClientException(ErrorCode.CATEGORY_PARENT_NOT_FOUND);
            }
            
            if (parent.getLevel() >= 3) {
                throw new ClientException(ErrorCode.CATEGORY_LEVEL_EXCEEDED);
            }
            
            if (hasChildren(id)) {
                throw new ClientException(ErrorCode.CATEGORY_HAS_CHILDREN_CANNOT_CHANGE_PARENT);
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
            throw new ClientException(ErrorCode.CATEGORY_SLUG_EXISTS);
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
            throw new ClientException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (hasChildren(id)) {
            throw new ClientException(ErrorCode.CATEGORY_HAS_CHILDREN_CANNOT_DELETE);
        }

        Long articleCount = articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getCategoryId, id)
                .eq(ArticleDO::getDelFlag, 0));
        if (articleCount > 0) {
            throw new ClientException(ErrorCode.CATEGORY_HAS_ARTICLES_CANNOT_DELETE);
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

        Map<Long, Long> articleCountMap = batchCountArticles(categories);

        return categories.stream().map(cat -> {
            CategoryRespDTO dto = BeanUtil.toBean(cat, CategoryRespDTO.class);
            dto.setArticleCount(articleCountMap.getOrDefault(cat.getId(), 0L));
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
            throw new ClientException(ErrorCode.CATEGORY_NOT_FOUND);
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
            throw new ClientException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        CategoryRespDTO dto = BeanUtil.toBean(categoryDO, CategoryRespDTO.class);
        dto.setArticleCount(articleMapper.selectCount(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getCategoryId, categoryDO.getId())
                .eq(ArticleDO::getDelFlag, 0)));
        return dto;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'query:' + #requestParam.hashCode()")
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

        Map<Long, Long> articleCountMap = batchCountArticles(categories);

        return categories.stream().map(cat -> {
            CategoryRespDTO dto = BeanUtil.toBean(cat, CategoryRespDTO.class);
            dto.setArticleCount(articleCountMap.getOrDefault(cat.getId(), 0L));
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
            throw new ClientException(ErrorCode.CATEGORY_NOT_FOUND);
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
        List<Long> ids = requestParam.getIds();
        Long existingCount = categoryMapper.selectCount(Wrappers.<CategoryDO>lambdaQuery()
                .in(CategoryDO::getId, ids)
                .eq(CategoryDO::getDelFlag, 0));
        if (existingCount < ids.size()) {
            throw new ClientException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        categoryMapper.update(null, Wrappers.<CategoryDO>lambdaUpdate()
                .in(CategoryDO::getId, ids)
                .set(CategoryDO::getStatus, requestParam.getStatus()));
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
            throw new ClientException(ErrorCode.CATEGORY_NOT_FOUND);
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
    @Cacheable(value = CACHE_NAME, key = "'children:' + #parentId")
    public List<CategoryRespDTO> getChildrenByParentId(Long parentId) {
        List<CategoryDO> categories = categoryMapper.selectList(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getParentId, parentId)
                .eq(CategoryDO::getDelFlag, 0)
                .orderByAsc(CategoryDO::getSortOrder)
                .orderByDesc(CategoryDO::getCreateTime));

        Map<Long, Long> articleCountMap = batchCountArticles(categories);

        return categories.stream().map(cat -> {
            CategoryRespDTO dto = BeanUtil.toBean(cat, CategoryRespDTO.class);
            dto.setArticleCount(articleCountMap.getOrDefault(cat.getId(), 0L));
            return dto;
        }).collect(Collectors.toList());
    }

    private Map<Long, Long> batchCountArticles(List<CategoryDO> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> categoryIds = categories.stream()
                .map(CategoryDO::getId)
                .collect(Collectors.toList());
        QueryWrapper<ArticleDO> wrapper = new QueryWrapper<>();
        wrapper.select("category_id", "COUNT(*) AS cnt")
                .eq("del_flag", 0)
                .in("category_id", categoryIds)
                .groupBy("category_id");
        List<Map<String, Object>> counts = articleMapper.selectMaps(wrapper);
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> map : counts) {
            Long categoryId = ((Number) map.get("category_id")).longValue();
            Long count = ((Number) map.get("cnt")).longValue();
            result.put(categoryId, count);
        }
        return result;
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
