package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.ArticleTagDO;
import io.github.somehow.mysite.dao.entity.CategoryDO;
import io.github.somehow.mysite.dao.entity.TagDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.service.ArticleSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", havingValue = "false")
public class DatabaseArticleSearchServiceImpl implements ArticleSearchService {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final UserFavoriteArticleMapper userFavoriteArticleMapper;
    private final ElasticsearchProperties elasticsearchProperties;

    @Override
    public IPage<ArticlePageQueryRespDTO> searchArticles(ArticlePageQueryReqDTO requestParam) {
        log.debug("[数据库搜索] 开始搜索文章，参数: keyword={}, searchType={}, categorySlug={}, tagSlug={}",
                requestParam.getKeyword(), requestParam.getSearchType(),
                requestParam.getCategorySlug(), requestParam.getTagSlug());

        Page<ArticleDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        LambdaQueryWrapper<ArticleDO> queryWrapper = Wrappers.<ArticleDO>lambdaQuery()
                .eq(ArticleDO::getDelFlag, 0)
                .eq(ArticleDO::getPublished, 1);

        String keyword = requestParam.getKeyword();
        String searchType = StrUtil.blankToDefault(requestParam.getSearchType(), "title");
        String categorySlug = requestParam.getCategorySlug();
        String tagSlug = requestParam.getTagSlug();

        if (StrUtil.isNotBlank(categorySlug)) {
            CategoryDO category = categoryMapper.selectOne(Wrappers.<CategoryDO>lambdaQuery()
                    .eq(CategoryDO::getSlug, categorySlug));
            if (category != null) {
                queryWrapper.eq(ArticleDO::getCategoryId, category.getId());
            }
        }

        if (StrUtil.isNotBlank(tagSlug)) {
            TagDO tag = tagMapper.selectOne(Wrappers.<TagDO>lambdaQuery()
                    .eq(TagDO::getSlug, tagSlug));
            if (tag != null) {
                List<ArticleTagDO> articleTags = articleTagMapper.selectList(Wrappers.<ArticleTagDO>lambdaQuery()
                        .eq(ArticleTagDO::getTagId, tag.getId())
                        .eq(ArticleTagDO::getDelFlag, 0));
                if (!CollectionUtils.isEmpty(articleTags)) {
                    List<Long> articleIds = articleTags.stream()
                            .map(ArticleTagDO::getArticleId)
                            .collect(Collectors.toList());
                    queryWrapper.in(ArticleDO::getId, articleIds);
                } else {
                    return new Page<>();
                }
            }
        }

        if (StrUtil.isNotBlank(keyword)) {
            keyword = keyword.toLowerCase();
            switch (searchType) {
                case "content":
                    queryWrapper.like(ArticleDO::getContent, keyword);
                    break;
                case "author":
                    List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                    if (!CollectionUtils.isEmpty(matchedUsers)) {
                        List<Long> authorIds = matchedUsers.stream()
                                .map(UserDO::getId)
                                .collect(Collectors.toList());
                        queryWrapper.in(ArticleDO::getAuthorId, authorIds);
                    } else {
                        return new Page<>();
                    }
                    break;
                default:
                    queryWrapper.like(ArticleDO::getTitle, keyword);
                    break;
            }
        }

        queryWrapper.orderByDesc(ArticleDO::getCreateTime);

        Page<ArticleDO> articlePage = articleMapper.selectPage(page, queryWrapper);

        IPage<ArticlePageQueryRespDTO> result = convertToDtoPage(articlePage);
        log.debug("[数据库搜索] 搜索完成，返回 {} 条记录", result.getRecords().size());
        return result;
    }

    private IPage<ArticlePageQueryRespDTO> convertToDtoPage(Page<ArticleDO> articlePage) {
        List<ArticleDO> articles = articlePage.getRecords();
        if (CollectionUtils.isEmpty(articles)) {
            return new Page<>();
        }

        List<Long> authorIds = articles.stream()
                .map(ArticleDO::getAuthorId)
                .distinct()
                .collect(Collectors.toList());

        List<UserDO> authors = userMapper.selectList(Wrappers.<UserDO>lambdaQuery()
                .in(UserDO::getId, authorIds));

        Set<Long> categoryIds = articles.stream()
                .map(ArticleDO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, CategoryDO> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            categoryMapper.selectList(Wrappers.<CategoryDO>lambdaQuery()
                    .in(CategoryDO::getId, categoryIds))
                    .forEach(c -> categoryMap.put(c.getId(), c));
        }

        List<ArticlePageQueryRespDTO> records = articles.stream()
                .map(article -> {
                    ArticlePageQueryRespDTO dto = new ArticlePageQueryRespDTO();
                    dto.setId(article.getId());
                    dto.setTitle(article.getTitle());
                    dto.setSummary(article.getSummary());
                    dto.setCoverImage(article.getCoverImage());
                    dto.setViewCount(article.getViewCount());
                    dto.setFavoriteCount(article.getFavoriteCount());
                    dto.setAuthorId(article.getAuthorId());
                    dto.setCreateTime(article.getCreateTime());
                    dto.setUpdateTime(article.getUpdateTime());

                    String authorName = authors.stream()
                            .filter(user -> user.getId().equals(article.getAuthorId()))
                            .map(UserDO::getUsername)
                            .findFirst()
                            .orElse("");
                    dto.setAuthorName(authorName);

                    if (article.getCategoryId() != null) {
                        CategoryDO cat = categoryMap.get(article.getCategoryId());
                        if (cat != null) {
                            dto.setCategoryName(cat.getName());
                            dto.setCategorySlug(cat.getSlug());
                        }
                    }

                    return dto;
                })
                .collect(Collectors.toList());

        String currentUserId = UserContext.getUserId();
        if (currentUserId != null && !records.isEmpty()) {
            List<String> articleIdStrs = records.stream()
                    .map(r -> r.getId().toString())
                    .collect(Collectors.toList());
            List<UserFavoriteArticleDO> favs = userFavoriteArticleMapper.selectList(
                    Wrappers.lambdaQuery(UserFavoriteArticleDO.class)
                            .eq(UserFavoriteArticleDO::getUserId, currentUserId)
                            .in(UserFavoriteArticleDO::getArticleId, articleIdStrs)
                            .eq(UserFavoriteArticleDO::getDelFlag, 0));
            Set<String> favoritedIds = favs.stream()
                    .map(f -> f.getArticleId().toString())
                    .collect(Collectors.toSet());
            for (ArticlePageQueryRespDTO rec : records) {
                rec.setIsFavorited(favoritedIds.contains(rec.getId().toString()));
            }
        }

        IPage<ArticlePageQueryRespDTO> result = new Page<>(articlePage.getCurrent(), articlePage.getSize(), articlePage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public void indexArticle(ArticleDO article) {
        log.debug("[数据库模式] 跳过索引文章: id={}, Elasticsearch已禁用", article.getId());
    }

    @Override
    public void updateArticle(ArticleDO article) {
        log.debug("[数据库模式] 跳过更新文章索引: id={}, Elasticsearch已禁用", article.getId());
    }

    @Override
    public void deleteArticle(Long articleId) {
        log.debug("[数据库模式] 跳过删除文章索引: id={}, Elasticsearch已禁用", articleId);
    }

    @Override
    public void syncAllArticles() {
        log.info("[数据库模式] Elasticsearch已禁用，跳过数据同步");
    }

    @Override
    public long count() {
        return articleMapper.selectCount(Wrappers.<ArticleDO>lambdaQuery()
                .eq(ArticleDO::getDelFlag, 0)
                .eq(ArticleDO::getPublished, 1));
    }

    @Override
    public boolean isEnabled() {
        return elasticsearchProperties.isEnabled();
    }
}
