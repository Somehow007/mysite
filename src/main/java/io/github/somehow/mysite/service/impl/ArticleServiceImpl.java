package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.*;
import io.github.somehow.mysite.dao.mapper.*;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArchiveRespDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleFavoriteRespDTO;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.ArticleService;
import io.github.somehow.mysite.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import io.github.somehow.mysite.utils.ReadingTimeCalculator;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, ArticleDO> implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleSearchService articleSearchService;
    private final UserMapper userMapper;
    private final UserFavoriteArticleMapper userFavoriteArticleMapper;
    private final CategoryMapper categoryMapper;
    private final CategoryService categoryService;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    @Transactional
    public void createArticle(ArticleCreateReqDTO requestParam) {
        if (Objects.isNull(requestParam)) {
            throw new ClientException(ErrorCode.ARTICLE_PARAM_REQUIRED);
        }
        if (StrUtil.isBlank(requestParam.getAuthorId())) {
            throw new ClientException(ErrorCode.ARTICLE_AUTHOR_REQUIRED);
        }
        if (StrUtil.isBlank(requestParam.getTitle())) {
            throw new ClientException(ErrorCode.ARTICLE_TITLE_REQUIRED);
        }
        if (StrUtil.isBlank(requestParam.getContent())) {
            throw new ClientException(ErrorCode.ARTICLE_CONTENT_REQUIRED);
        }

        ArticleDO articleDO = BeanUtil.toBean(requestParam, ArticleDO.class);
        articleDO.setId(IdUtil.getSnowflakeNextId());
        articleDO.setAuthorId(Long.parseLong(requestParam.getAuthorId()));
        if (articleDO.getPublished() == null) {
            articleDO.setPublished(1);
        }
        articleDO.setViewCount(0);
        articleDO.setFavoriteCount(0);
        articleDO.setReadingTime(calculateReadingTime(requestParam.getContent()));
        articleMapper.insert(articleDO);

        if (!CollectionUtils.isEmpty(requestParam.getTagIds())) {
            for (Long tagId : requestParam.getTagIds()) {
                ArticleTagDO at = ArticleTagDO.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .articleId(articleDO.getId())
                        .tagId(tagId)
                        .build();
                articleTagMapper.insert(at);
            }
        }

        articleSearchService.indexArticle(articleDO);
        categoryService.evictCategoryCache();
    }

    @Override
    @Transactional
    public void updateArticle(ArticleUpdateReqDTO requestParam) {
        if (Objects.isNull(requestParam)) {
            throw new ClientException(ErrorCode.ARTICLE_PARAM_REQUIRED);
        }

        checkArticleOwnership(requestParam.getId());

        LambdaUpdateWrapper<ArticleDO> updateWrapper = Wrappers.lambdaUpdate(ArticleDO.class)
                .eq(ArticleDO::getId, requestParam.getId())
                .set(StrUtil.isNotBlank(requestParam.getTitle()), ArticleDO::getTitle, requestParam.getTitle())
                .set(StrUtil.isNotBlank(requestParam.getContent()), ArticleDO::getContent, requestParam.getContent())
                .set(StrUtil.isNotBlank(requestParam.getSummary()), ArticleDO::getSummary, requestParam.getSummary())
                .set(requestParam.getCoverImage() != null, ArticleDO::getCoverImage, requestParam.getCoverImage())
                .set(requestParam.getCategoryId() != null, ArticleDO::getCategoryId, requestParam.getCategoryId())
                .set(!Objects.isNull(requestParam.getPublished()), ArticleDO::getPublished, requestParam.getPublished())
                .eq(ArticleDO::getDelFlag, 0);
        if (StrUtil.isNotBlank(requestParam.getContent())) {
            updateWrapper.set(ArticleDO::getReadingTime, calculateReadingTime(requestParam.getContent()));
        }
        int rows = baseMapper.update(updateWrapper);
        if (rows <= 0) {
            throw new ClientException(ErrorCode.ARTICLE_UPDATE_FAILED);
        }

        if (requestParam.getTagIds() != null) {
            articleTagMapper.delete(Wrappers.lambdaQuery(ArticleTagDO.class)
                    .eq(ArticleTagDO::getArticleId, requestParam.getId()));
            for (Long tagId : requestParam.getTagIds()) {
                ArticleTagDO at = ArticleTagDO.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .articleId(requestParam.getId())
                        .tagId(tagId)
                        .build();
                articleTagMapper.insert(at);
            }
        }

        ArticleDO updatedArticle = baseMapper.selectOne(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, requestParam.getId())
                .eq(ArticleDO::getDelFlag, 0));

        if (updatedArticle != null) {
            articleSearchService.updateArticle(updatedArticle);
        }
        categoryService.evictCategoryCache();
    }

    @Override
    @Transactional
    public void deleteArticle(Long id) {
        checkArticleOwnership(id);

        LambdaQueryWrapper<ArticleDO> deleteWrapper = Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, id);

        int rows = baseMapper.delete(deleteWrapper);
        if (rows <= 0) {
            throw new ClientException(ErrorCode.ARTICLE_DELETE_FAILED);
        }

        articleTagMapper.delete(Wrappers.lambdaQuery(ArticleTagDO.class)
                .eq(ArticleTagDO::getArticleId, id));

        userFavoriteArticleMapper.delete(Wrappers.lambdaQuery(UserFavoriteArticleDO.class)
                .eq(UserFavoriteArticleDO::getArticleId, id));

        articleSearchService.deleteArticle(id);
        categoryService.evictCategoryCache();
    }

    @Override
    public IPage<ArticlePageQueryRespDTO> pageQueryArticle(ArticlePageQueryReqDTO requestParam) {
        return articleSearchService.searchArticles(requestParam);
    }

    @Override
    public IPage<ArticlePageQueryRespDTO> pageQueryFavoriteArticle(ArticleFavoritePageQueryReqDTO requestParam) {
        IPage<ArticlePageQueryRespDTO> result = baseMapper.pageFavoriteArticleResults(requestParam);
        if (result.getRecords() != null) {
            for (ArticlePageQueryRespDTO dto : result.getRecords()) {
                dto.setIsFavorited(true);
            }
        }
        return result;
    }

    @Override
    public ArticleSelectRespDTO selectOneArticle(Long id) {
        LambdaQueryWrapper<ArticleDO> queryWrapper = Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, id)
                .eq(ArticleDO::getDelFlag, 0);
        ArticleDO articleDO = baseMapper.selectOne(queryWrapper);
        if (Objects.isNull(articleDO)) {
            throw new ClientException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        baseMapper.incrementViewCount(id, 1);

        ArticleSelectRespDTO result = BeanUtil.toBean(articleDO, ArticleSelectRespDTO.class);

        String currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            UserFavoriteArticleDO fav = userFavoriteArticleMapper.selectOne(Wrappers.lambdaQuery(UserFavoriteArticleDO.class)
                    .eq(UserFavoriteArticleDO::getArticleId, id)
                    .eq(UserFavoriteArticleDO::getUserId, currentUserId)
                    .eq(UserFavoriteArticleDO::getDelFlag, 0));
            result.setIsFavorited(fav != null);
        } else {
            result.setIsFavorited(false);
        }

        UserDO author = userMapper.selectById(articleDO.getAuthorId());
        if (author != null) {
            result.setAuthorName(author.getUsername());
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

        return result;
    }

    @Override
    @Transactional
    public ArticleFavoriteRespDTO favoriteArticle(ArticleFavoriteReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getArticleId()) || StrUtil.isBlank(requestParam.getUserId())) {
            throw new ClientException(ErrorCode.ARTICLE_FAVORITE_PARAM_INCOMPLETE);
        }
        Long articleId = Long.parseLong(requestParam.getArticleId());
        Long userId = Long.parseLong(requestParam.getUserId());

        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null || article.getDelFlag() != 0) {
            throw new ClientException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        UserFavoriteArticleDO existing = userFavoriteArticleMapper.selectByUserAndArticle(userId, articleId);

        if (existing != null && existing.getDelFlag() == 0) {
            userFavoriteArticleMapper.softDeleteById(existing.getId());
            articleMapper.decrementFavoriteCount(articleId, 1);
            log.debug("存在且已收藏过，数量-1");

            return ArticleFavoriteRespDTO.builder().favorited(false).favoriteCount(getFavoriteCount(articleId)).build();
        }

        if (existing != null && existing.getDelFlag() == 1) {
            userFavoriteArticleMapper.softRestoreById(existing.getId());
            articleMapper.incrementFavoriteCount(articleId, 1);
            log.debug("存在且已删除，数量+1");
            return ArticleFavoriteRespDTO.builder().favorited(true).favoriteCount(getFavoriteCount(articleId)).build();
        }

        try {
            UserFavoriteArticleDO record = new UserFavoriteArticleDO();
            record.setId(IdUtil.getSnowflakeNextId());
            record.setArticleId(articleId);
            record.setUserId(userId);
            record.setDelFlag(0);
            userFavoriteArticleMapper.insert(record);
            articleMapper.incrementFavoriteCount(articleId, 1);
            log.debug("收藏成功，+1");
        } catch (DuplicateKeyException e) {
            log.info("Duplicate favorite request, userId: {}, articleId: {}", userId, articleId);
            UserFavoriteArticleDO duplicate = userFavoriteArticleMapper.selectByUserAndArticle(userId, articleId);
            if (duplicate != null) {
                if (duplicate.getDelFlag() == 1) {
                    userFavoriteArticleMapper.softRestoreById(duplicate.getId());
                    articleMapper.incrementFavoriteCount(articleId, 1);
                    return ArticleFavoriteRespDTO.builder().favorited(true).favoriteCount(getFavoriteCount(articleId)).build();
                } else {
                    return ArticleFavoriteRespDTO.builder().favorited(true).favoriteCount(getFavoriteCount(articleId)).build();
                }
            }
            throw new ClientException(ErrorCode.OPERATION_TOO_FREQUENT);
        }
        return ArticleFavoriteRespDTO.builder().favorited(true).favoriteCount(getFavoriteCount(articleId)).build();
    }

    @Override
    public Map<String, Boolean> checkFavoriteStatus(String userId, List<String> articleIds) {
        if (StrUtil.isBlank(userId) || CollectionUtils.isEmpty(articleIds)) {
            return Map.of();
        }
        List<UserFavoriteArticleDO> favorites = userFavoriteArticleMapper.selectList(Wrappers.lambdaQuery(UserFavoriteArticleDO.class)
                .eq(UserFavoriteArticleDO::getUserId, userId)
                .in(UserFavoriteArticleDO::getArticleId, articleIds)
                .eq(UserFavoriteArticleDO::getDelFlag, 0));
        Set<String> favoritedIds = favorites.stream()
                .map(fav -> fav.getArticleId().toString())
                .collect(Collectors.toSet());
        Map<String, Boolean> result = new HashMap<>();
        for (String articleId : articleIds) {
            result.put(articleId, favoritedIds.contains(articleId));
        }
        return result;
    }

    @Override
    public List<ArchiveRespDTO> getArchive() {
        List<ArticleDO> articles = baseMapper.selectList(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getDelFlag, 0)
                .eq(ArticleDO::getPublished, 1)
                .orderByDesc(ArticleDO::getCreateTime));

        SimpleDateFormat yearFmt = new SimpleDateFormat("yyyy");
        SimpleDateFormat monthFmt = new SimpleDateFormat("MM");

        List<Long> authorIds = articles.stream()
                .map(ArticleDO::getAuthorId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> authorNameMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            userMapper.selectBatchIds(authorIds).forEach(user ->
                    authorNameMap.put(user.getId(), user.getUsername()));
        }

        Map<String, Map<String, List<ArchiveRespDTO.ArchiveArticle>>> grouped = new LinkedHashMap<>();
        for (ArticleDO article : articles) {
            String year = yearFmt.format(article.getCreateTime());
            String month = monthFmt.format(article.getCreateTime());

            String authorName = authorNameMap.getOrDefault(article.getAuthorId(), "");

            ArchiveRespDTO.ArchiveArticle aa = ArchiveRespDTO.ArchiveArticle.builder()
                    .id(article.getId())
                    .title(article.getTitle())
                    .summary(article.getSummary())
                    .coverImage(article.getCoverImage())
                    .authorName(authorName)
                    .createTime(article.getCreateTime())
                    .build();

            grouped.computeIfAbsent(year, k -> new LinkedHashMap<>())
                    .computeIfAbsent(month, k -> new ArrayList<>())
                    .add(aa);
        }

        return grouped.entrySet().stream()
                .map(yearEntry -> ArchiveRespDTO.builder()
                        .year(yearEntry.getKey())
                        .months(yearEntry.getValue().entrySet().stream()
                                .map(monthEntry -> ArchiveRespDTO.ArchiveMonth.builder()
                                        .month(monthEntry.getKey())
                                        .articles(monthEntry.getValue())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    private Integer calculateReadingTime(String content) {
        return ReadingTimeCalculator.calculate(content);
    }

    private Integer getFavoriteCount(Long articleId) {
        ArticleDO article = articleMapper.selectOne(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDelFlag, 0));
        return article != null ? article.getFavoriteCount() : 0;
    }

    private void checkArticleOwnership(Long articleId) {
        UserRole currentRole = UserContext.getRole();
        if (UserRole.DEVELOPER.equals(currentRole)) {
            return;
        }

        String currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new ClientException(ErrorCode.ARTICLE_OWNERSHIP_VERIFY_FAILED);
        }

        ArticleDO article = baseMapper.selectOne(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDelFlag, 0));
        if (article == null) {
            throw new ClientException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        if (!currentUserId.equals(article.getAuthorId().toString())) {
            throw new ClientException(ErrorCode.ARTICLE_PERMISSION_DENIED);
        }
    }
}
