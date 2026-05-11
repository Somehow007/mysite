package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.CategoryDO;
import io.github.somehow.mysite.dao.entity.TagDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import io.github.somehow.mysite.elasticsearch.repository.ArticleEsRepository;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.elasticsearch.ArticleDocument;
import io.github.somehow.mysite.service.ArticleSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
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
@ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchArticleSearchServiceImpl implements ArticleSearchService {

    private final ArticleEsRepository articleEsRepository;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final UserFavoriteArticleMapper userFavoriteArticleMapper;
    private final ElasticsearchProperties elasticsearchProperties;

    @Override
    public IPage<ArticlePageQueryRespDTO> searchArticles(ArticlePageQueryReqDTO requestParam) {
        log.debug("[ES搜索] 开始搜索文章，参数: keyword={}, searchType={}, categorySlug={}, tagSlug={}",
                requestParam.getKeyword(), requestParam.getSearchType(),
                requestParam.getCategorySlug(), requestParam.getTagSlug());

        PageRequest pageRequest = PageRequest.of(
                (int) (requestParam.getCurrent() - 1),
                (int) requestParam.getSize());

        String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "");
        String searchType = StrUtil.blankToDefault(requestParam.getSearchType(), "title");
        String categorySlug = requestParam.getCategorySlug();
        String tagSlug = requestParam.getTagSlug();

        org.springframework.data.domain.Page<ArticleDocument> esPage;

        if (StrUtil.isNotBlank(tagSlug)) {
            esPage = searchByTagSlug(tagSlug, keyword, searchType, pageRequest);
        } else if (StrUtil.isNotBlank(categorySlug)) {
            esPage = searchByCategorySlug(categorySlug, keyword, searchType, pageRequest);
        } else {
            esPage = searchByKeyword(keyword, searchType, pageRequest);
        }

        IPage<ArticlePageQueryRespDTO> result = buildArticlePageResult(esPage, requestParam);
        log.debug("[ES搜索] 搜索完成，返回 {} 条记录", result.getRecords().size());
        return result;
    }

    private org.springframework.data.domain.Page<ArticleDocument> searchByKeyword(String keyword, String searchType, PageRequest pageRequest) {
        if (StrUtil.isBlank(keyword)) {
            return articleEsRepository.findAll(pageRequest);
        }

        keyword = keyword.toLowerCase();
        switch (searchType) {
            case "content":
                return articleEsRepository.findByContentContaining(keyword, pageRequest);
            case "author":
                List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                if (!CollectionUtils.isEmpty(matchedUsers)) {
                    List<String> authorIds = matchedUsers.stream()
                            .map(user -> user.getId().toString())
                            .collect(Collectors.toList());
                    return articleEsRepository.findByAuthorIdIn(authorIds, pageRequest);
                }
                return org.springframework.data.domain.Page.empty();
            default:
                return articleEsRepository.findByTitleContaining(keyword, pageRequest);
        }
    }

    private org.springframework.data.domain.Page<ArticleDocument> searchByCategorySlug(String categorySlug, String keyword, String searchType, PageRequest pageRequest) {
        CategoryDO category = categoryMapper.selectOne(Wrappers.<CategoryDO>lambdaQuery()
                .eq(CategoryDO::getSlug, categorySlug));
        if (category == null) {
            return org.springframework.data.domain.Page.empty();
        }
        String categoryId = category.getId().toString();

        if (StrUtil.isBlank(keyword)) {
            return articleEsRepository.findByCategoryId(categoryId, pageRequest);
        }

        keyword = keyword.toLowerCase();
        switch (searchType) {
            case "content":
                return articleEsRepository.findByCategoryIdAndContentContaining(categoryId, keyword, pageRequest);
            case "author":
                List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                if (!CollectionUtils.isEmpty(matchedUsers)) {
                    List<String> authorIds = matchedUsers.stream()
                            .map(user -> user.getId().toString())
                            .collect(Collectors.toList());
                    return articleEsRepository.findByCategoryIdAndAuthorIdIn(categoryId, authorIds, pageRequest);
                }
                return org.springframework.data.domain.Page.empty();
            default:
                return articleEsRepository.findByCategoryIdAndTitleContaining(categoryId, keyword, pageRequest);
        }
    }

    private org.springframework.data.domain.Page<ArticleDocument> searchByTagSlug(String tagSlug, String keyword, String searchType, PageRequest pageRequest) {
        TagDO tag = tagMapper.selectOne(Wrappers.<TagDO>lambdaQuery()
                .eq(TagDO::getSlug, tagSlug));
        if (tag == null) {
            return org.springframework.data.domain.Page.empty();
        }

        List<ArticleDO> articleTags = articleMapper.selectList(Wrappers.<ArticleDO>lambdaQuery()
                .eq(ArticleDO::getDelFlag, 0));

        if (CollectionUtils.isEmpty(articleTags)) {
            return org.springframework.data.domain.Page.empty();
        }

        List<String> articleIds = articleTags.stream()
                .map(at -> at.getId().toString())
                .collect(Collectors.toList());

        if (StrUtil.isBlank(keyword)) {
            return articleEsRepository.findByIdIn(articleIds, pageRequest);
        }

        keyword = keyword.toLowerCase();
        switch (searchType) {
            case "content":
                return articleEsRepository.findByIdInAndContentContaining(articleIds, keyword, pageRequest);
            case "author":
                List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                if (!CollectionUtils.isEmpty(matchedUsers)) {
                    List<String> authorIds = matchedUsers.stream()
                            .map(user -> user.getId().toString())
                            .collect(Collectors.toList());
                    return articleEsRepository.findByIdInAndAuthorIdIn(articleIds, authorIds, pageRequest);
                }
                return org.springframework.data.domain.Page.empty();
            default:
                return articleEsRepository.findByIdInAndTitleContaining(articleIds, keyword, pageRequest);
        }
    }

    private IPage<ArticlePageQueryRespDTO> buildArticlePageResult(
            org.springframework.data.domain.Page<ArticleDocument> esPage,
            ArticlePageQueryReqDTO requestParam) {
        if (!esPage.hasContent()) {
            return new Page<>();
        }

        List<Long> articleIds = esPage.getContent().stream()
                .map(doc -> Long.valueOf(doc.getId()))
                .collect(Collectors.toList());

        List<ArticleDO> articles = articleMapper.selectList(Wrappers.<ArticleDO>lambdaQuery()
                .in(ArticleDO::getId, articleIds)
                .eq(ArticleDO::getDelFlag, 0));

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
                .sorted((a, b) -> {
                    if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
                    if (a.getCreateTime() == null) return 1;
                    if (b.getCreateTime() == null) return -1;
                    return b.getCreateTime().compareTo(a.getCreateTime());
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

        IPage<ArticlePageQueryRespDTO> result = new Page<>(requestParam.getCurrent(), requestParam.getSize(), esPage.getTotalElements());
        result.setRecords(records);
        return result;
    }

    @Override
    public void indexArticle(ArticleDO article) {
        log.info("[ES索引] 索引文章: id={}, title={}", article.getId(), article.getTitle());
        ArticleDocument doc = convertToDocument(article);
        articleEsRepository.save(doc);
        log.debug("[ES索引] 文章索引成功: id={}", article.getId());
    }

    @Override
    public void updateArticle(ArticleDO article) {
        log.info("[ES更新] 更新文章索引: id={}, title={}", article.getId(), article.getTitle());
        ArticleDocument doc = convertToDocument(article);
        articleEsRepository.save(doc);
        log.debug("[ES更新] 文章索引更新成功: id={}", article.getId());
    }

    @Override
    public void deleteArticle(Long articleId) {
        log.info("[ES删除] 删除文章索引: id={}", articleId);
        articleEsRepository.deleteById(articleId.toString());
        log.debug("[ES删除] 文章索引删除成功: id={}", articleId);
    }

    @Override
    public void syncAllArticles() {
        log.info("[ES同步] 开始同步所有文章到Elasticsearch...");
        List<ArticleDO> articles = articleMapper.selectList(
                new LambdaQueryWrapper<ArticleDO>()
                        .eq(ArticleDO::getDelFlag, 0)
                        .orderByDesc(ArticleDO::getCreateTime));

        if (articles.isEmpty()) {
            log.warn("[ES同步] 数据库中没有文章数据");
            return;
        }

        List<ArticleDocument> documents = articles.stream()
                .map(this::convertToDocument)
                .collect(Collectors.toList());

        articleEsRepository.saveAll(documents);
        log.info("[ES同步] 成功同步 {} 篇文章到Elasticsearch", documents.size());
    }

    @Override
    public long count() {
        return articleEsRepository.count();
    }

    @Override
    public boolean isEnabled() {
        return elasticsearchProperties.isEnabled();
    }

    private ArticleDocument convertToDocument(ArticleDO article) {
        return ArticleDocument.builder()
                .id(article.getId().toString())
                .title(article.getTitle())
                .content(article.getContent())
                .summary(article.getSummary())
                .authorId(article.getAuthorId().toString())
                .categoryId(article.getCategoryId() != null ? article.getCategoryId().toString() : null)
                .createTime(article.getCreateTime())
                .build();
    }
}
