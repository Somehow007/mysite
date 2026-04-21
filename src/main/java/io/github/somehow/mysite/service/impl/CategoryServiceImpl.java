package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.CategoryDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dto.req.category.CategoryCreateReqDTO;
import io.github.somehow.mysite.dto.req.category.CategoryUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.category.CategoryRespDTO;
import io.github.somehow.mysite.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public void createCategory(CategoryCreateReqDTO requestParam) {
        CategoryDO categoryDO = BeanUtil.toBean(requestParam, CategoryDO.class);
        categoryDO.setId(IdUtil.getSnowflakeNextId());
        try {
            categoryMapper.insert(categoryDO);
        } catch (DuplicateKeyException e) {
            throw new ClientException("分类别名已存在: " + requestParam.getSlug());
        }
    }

    @Override
    public void updateCategory(Long id, CategoryUpdateReqDTO requestParam) {
        CategoryDO existing = categoryMapper.selectOne(Wrappers.lambdaQuery(CategoryDO.class)
                .eq(CategoryDO::getId, id)
                .eq(CategoryDO::getDelFlag, 0));
        if (Objects.isNull(existing)) {
            throw new ClientException("分类不存在");
        }

        if (requestParam.getName() != null) existing.setName(requestParam.getName());
        if (requestParam.getSlug() != null) existing.setSlug(requestParam.getSlug());
        if (requestParam.getDescription() != null) existing.setDescription(requestParam.getDescription());
        if (requestParam.getSortOrder() != null) existing.setSortOrder(requestParam.getSortOrder());

        try {
            categoryMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new ClientException("分类别名已存在: " + requestParam.getSlug());
        }
    }

    @Override
    public void deleteCategory(Long id) {
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
}
