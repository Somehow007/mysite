package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.*;
import io.github.somehow.mysite.dao.mapper.*;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArchiveRespDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    @Transactional
    public void createArticle(ArticleCreateReqDTO requestParam) {
        if (Objects.isNull(requestParam)) {
            throw new ClientException("Parameter is required.");
        }
        if (StrUtil.isBlank(requestParam.getAuthorId())) {
            throw new ClientException("Author is required!");
        }
        if (StrUtil.isBlank(requestParam.getTitle())) {
            throw new ClientException("Title is required!");
        }
        if (StrUtil.isBlank(requestParam.getContent())) {
            throw new ClientException("Content is required!");
        }

        ArticleDO articleDO = BeanUtil.toBean(requestParam, ArticleDO.class);
        articleDO.setId(IdUtil.getSnowflakeNextId());
        articleDO.setAuthorId(Long.parseLong(requestParam.getAuthorId()));
        if (articleDO.getPublished() == null) {
            articleDO.setPublished(1);
        }
        articleDO.setViewCount(0);
        articleDO.setFavoriteCount(0);
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
    }

    @Override
    @Transactional
    public void updateArticle(ArticleUpdateReqDTO requestParam) {
        if (Objects.isNull(requestParam)) {
            throw new ClientException("更新失败，未传递更新参数");
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
        int rows = baseMapper.update(updateWrapper);
        if (rows <= 0) {
            throw new ClientException("更新文章失败，文章不存在");
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
    }

    @Override
    public void deleteArticle(Long id) {
        checkArticleOwnership(id);

        LambdaUpdateWrapper<ArticleDO> updateWrapper = Wrappers.lambdaUpdate(ArticleDO.class)
                .eq(ArticleDO::getId, id)
                .eq(ArticleDO::getDelFlag, 0);
        ArticleDO articleDO = new ArticleDO();
        articleDO.setDelFlag(1);

        int rows = baseMapper.update(articleDO, updateWrapper);
        if (rows <= 0) {
            throw new ClientException("删除文章失败，文章不存在");
        }

        articleSearchService.deleteArticle(id);
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
            throw new ClientException("获取文章信息失败，文章不存在");
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
    public void favoriteArticle(ArticleFavoriteReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getArticleId()) || StrUtil.isBlank(requestParam.getUserId())) {
            throw new ClientException("收藏操作失败，参数不完整");
        }
        Long articleId = Long.parseLong(requestParam.getArticleId());
        Long userId = Long.parseLong(requestParam.getUserId());

        UserFavoriteArticleDO existing = userFavoriteArticleMapper.selectOne(
                Wrappers.lambdaQuery(UserFavoriteArticleDO.class)
                        .eq(UserFavoriteArticleDO::getArticleId, articleId)
                        .eq(UserFavoriteArticleDO::getUserId, userId));

        if (existing == null) {
            UserFavoriteArticleDO record = new UserFavoriteArticleDO();
            record.setId(IdUtil.getSnowflakeNextId());
            record.setArticleId(articleId);
            record.setUserId(userId);
            try {
                userFavoriteArticleMapper.insert(record);
            } catch (DuplicateKeyException e) {
                throw new ClientException("已经收藏过了，请刷新页面后重试");
            }
            articleMapper.incrementFavoriteCount(articleId, 1);
        } else if (existing.getDelFlag() == 0) {
            int rows = userFavoriteArticleMapper.update(
                    Wrappers.lambdaUpdate(UserFavoriteArticleDO.class)
                            .eq(UserFavoriteArticleDO::getId, existing.getId())
                            .eq(UserFavoriteArticleDO::getDelFlag, 0)
                            .set(UserFavoriteArticleDO::getDelFlag, 1));
            if (rows > 0) {
                articleMapper.decrementFavoriteCount(articleId, 1);
            }
        } else {
            int rows = userFavoriteArticleMapper.update(
                    Wrappers.lambdaUpdate(UserFavoriteArticleDO.class)
                            .eq(UserFavoriteArticleDO::getId, existing.getId())
                            .eq(UserFavoriteArticleDO::getDelFlag, 1)
                            .set(UserFavoriteArticleDO::getDelFlag, 0)
                            .set(UserFavoriteArticleDO::getCreateTime, new Date()));
            if (rows > 0) {
                articleMapper.incrementFavoriteCount(articleId, 1);
            }
        }
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

        Map<Long, String> authorNameCache = new HashMap<>();

        Map<String, Map<String, List<ArchiveRespDTO.ArchiveArticle>>> grouped = new LinkedHashMap<>();
        for (ArticleDO article : articles) {
            String year = yearFmt.format(article.getCreateTime());
            String month = monthFmt.format(article.getCreateTime());

            String authorName = authorNameCache.computeIfAbsent(article.getAuthorId(), aid -> {
                UserDO user = userMapper.selectById(aid);
                return user != null ? user.getUsername() : "";
            });

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

    private void checkArticleOwnership(Long articleId) {
        UserRole currentRole = UserContext.getRole();
        if (UserRole.DEVELOPER.equals(currentRole)) {
            return;
        }

        String currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new ClientException("无法验证文章所有权，请重新登录");
        }

        ArticleDO article = baseMapper.selectOne(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDelFlag, 0));
        if (article == null) {
            throw new ClientException("文章不存在");
        }

        if (!currentUserId.equals(article.getAuthorId().toString())) {
            throw new ClientException("权限不足，只能操作自己的文章");
        }
    }
}
