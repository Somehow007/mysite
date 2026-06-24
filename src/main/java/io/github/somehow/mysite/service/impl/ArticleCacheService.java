package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.*;
import io.github.somehow.mysite.dao.mapper.*;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleCacheService {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CollectionService collectionService;
    private final CollectionArticleMapper collectionArticleMapper;

    @Cacheable(value = "article_detail", key = "#id")
    public ArticleSelectRespDTO getArticleDetail(Long id) {
        LambdaQueryWrapper<ArticleDO> queryWrapper = Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, id)
                .eq(ArticleDO::getDelFlag, 0);
        ArticleDO articleDO = articleMapper.selectOne(queryWrapper);
        if (Objects.isNull(articleDO)) {
            throw new ClientException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        ArticleSelectRespDTO result = BeanUtil.toBean(articleDO, ArticleSelectRespDTO.class);

        if (articleDO.getAuthorId() != null) {
            UserDO author = userMapper.selectById(articleDO.getAuthorId());
            if (author != null) {
                result.setAuthorName(author.getUsername());
            }
        }

        if (articleDO.getCategoryId() != null) {
            CategoryDO category = categoryMapper.selectById(articleDO.getCategoryId());
            if (category != null) {
                result.setCategoryName(category.getName());
                result.setCategorySlug(category.getSlug());
            }
        }

        List<ArticleTagDO> articleTags = articleTagMapper.selectList(Wrappers.lambdaQuery(ArticleTagDO.class)
                .eq(ArticleTagDO::getArticleId, id)
                .eq(ArticleTagDO::getDelFlag, 0));
        if (!CollectionUtils.isEmpty(articleTags)) {
            List<Long> tagIds = articleTags.stream().map(ArticleTagDO::getTagId).collect(Collectors.toList());
            List<TagDO> tags = tagMapper.selectBatchIds(tagIds);
            result.setTags(tags.stream().map(tag -> {
                ArticleSelectRespDTO.TagInfo tagInfo = new ArticleSelectRespDTO.TagInfo();
                tagInfo.setId(tag.getId());
                tagInfo.setName(tag.getName());
                tagInfo.setSlug(tag.getSlug());
                return tagInfo;
            }).collect(Collectors.toList()));
        }

        CollectionDO collection = collectionService.getCollectionByArticleId(id);
        if (collection != null) {
            result.setCollectionId(collection.getId());
            result.setCollectionTitle(collection.getTitle());
            CollectionArticleDO ca = collectionArticleMapper.selectOne(Wrappers.lambdaQuery(CollectionArticleDO.class)
                    .eq(CollectionArticleDO::getArticleId, id)
                    .eq(CollectionArticleDO::getCollectionId, collection.getId())
                    .eq(CollectionArticleDO::getDelFlag, 0));
            if (ca != null) {
                result.setCollectionSortOrder(ca.getSortOrder());
            }
        }

        return result;
    }

    @CacheEvict(value = "article_detail", key = "#id")
    public void evictArticleDetail(Long id) {
    }
}
