package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.CollectionArticleDO;
import io.github.somehow.mysite.dao.entity.CollectionDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.CollectionArticleMapper;
import io.github.somehow.mysite.dao.mapper.CollectionMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleBatchReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleSortReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionCreateReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.collection.ArticleNavInfoRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionDetailRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionPageQueryRespDTO;
import io.github.somehow.mysite.service.CollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl extends ServiceImpl<CollectionMapper, CollectionDO> implements CollectionService {

    private final CollectionMapper collectionMapper;
    private final CollectionArticleMapper collectionArticleMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    private static final String CACHE_DETAIL = "collection_detail";
    private static final String CACHE_ARTICLES = "collection_articles";
    private static final String CACHE_NAV = "article_nav";
    private static final String CACHE_HOME = "home_collections";

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_HOME}, allEntries = true)
    public Long createCollection(CollectionCreateReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getTitle())) {
            throw new ClientException(ErrorCode.COLLECTION_TITLE_REQUIRED);
        }

        String currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new ClientException(ErrorCode.COLLECTION_PERMISSION_DENIED);
        }

        CollectionDO collectionDO = BeanUtil.toBean(requestParam, CollectionDO.class);
        collectionDO.setId(IdUtil.getSnowflakeNextId());
        collectionDO.setAuthorId(Long.parseLong(currentUserId));
        collectionDO.setArticleCount(0);
        if (collectionDO.getSortOrder() == null) {
            collectionDO.setSortOrder(0);
        }
        if (StrUtil.isBlank(collectionDO.getCoverImage())) {
            collectionDO.setCoverImage(null);
        }
        collectionMapper.insert(collectionDO);
        return collectionDO.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_DETAIL, CACHE_ARTICLES, CACHE_HOME, CACHE_NAV}, allEntries = true)
    public void updateCollection(Long id, CollectionUpdateReqDTO requestParam) {
        CollectionDO existing = getCollectionOrThrow(id);
        checkCollectionOwnership(existing);

        if (requestParam.getTitle() != null) existing.setTitle(requestParam.getTitle());
        if (requestParam.getDescription() != null) existing.setDescription(requestParam.getDescription());
        if (requestParam.getCoverImage() != null) {
            existing.setCoverImage(StrUtil.isBlank(requestParam.getCoverImage()) ? null : requestParam.getCoverImage());
        }
        if (requestParam.getSortOrder() != null) existing.setSortOrder(requestParam.getSortOrder());

        collectionMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_DETAIL, CACHE_ARTICLES, CACHE_HOME, CACHE_NAV}, allEntries = true)
    public void deleteCollection(Long id) {
        CollectionDO existing = getCollectionOrThrow(id);
        checkCollectionOwnership(existing);

        // 软删除合集：使用 deleteById 触发 MyBatis-Plus 逻辑删除
        // 注意：updateById 不会更新 logic-delete-field(delFlag)，必须使用 deleteById
        collectionMapper.deleteById(id);

        // 物理删除关联记录
        collectionArticleMapper.physicalDeleteByCollectionId(id);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_HOME, key = "'page:' + #requestParam.current + ':' + #requestParam.size + ':' + #requestParam.keyword + ':' + #requestParam.authorId + ':' + #requestParam.sortBy")
    public IPage<CollectionPageQueryRespDTO> pageQueryCollection(CollectionPageQueryReqDTO requestParam) {
        Page<CollectionPageQueryRespDTO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<CollectionPageQueryRespDTO> result = collectionMapper.selectCollectionsPage(
                page,
                requestParam.getKeyword(),
                requestParam.getAuthorId(),
                requestParam.getSortBy());

        if (result == null || CollectionUtils.isEmpty(result.getRecords())) {
            return new Page<>();
        }

        // 确保 totalViewCount 不为 null（无文章的合集）
        result.getRecords().forEach(c -> {
            if (c.getTotalViewCount() == null) {
                c.setTotalViewCount(0L);
            }
            if (c.getAuthorName() == null) {
                c.setAuthorName("");
            }
        });

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_DETAIL, key = "#id + ':' + #current + ':' + #size")
    public CollectionDetailRespDTO getCollectionDetail(Long id, Integer current, Integer size) {
        CollectionDO collection = getCollectionOrThrow(id);

        CollectionDetailRespDTO detail = BeanUtil.toBean(collection, CollectionDetailRespDTO.class);

        // 查询作者名
        UserDO author = userMapper.selectById(collection.getAuthorId());
        if (author != null) {
            detail.setAuthorName(author.getUsername());
        }

        // 查询合集中的文章
        LambdaQueryWrapper<CollectionArticleDO> caWrapper = Wrappers.<CollectionArticleDO>lambdaQuery()
                .eq(CollectionArticleDO::getCollectionId, id)
                .eq(CollectionArticleDO::getDelFlag, 0)
                .orderByAsc(CollectionArticleDO::getSortOrder);

        List<CollectionArticleDO> collectionArticles = collectionArticleMapper.selectList(caWrapper);

        if (!CollectionUtils.isEmpty(collectionArticles)) {
            List<Long> articleIds = collectionArticles.stream()
                    .map(CollectionArticleDO::getArticleId)
                    .collect(Collectors.toList());

            // 分页处理
            int totalArticles = articleIds.size();
            int fromIndex = (current - 1) * size;
            int toIndex = Math.min(fromIndex + size, totalArticles);

            List<Long> pagedArticleIds;
            if (fromIndex >= totalArticles) {
                pagedArticleIds = Collections.emptyList();
            } else {
                pagedArticleIds = articleIds.subList(fromIndex, toIndex);
            }

            // 构建 sortOrder 映射
            Map<Long, Integer> sortOrderMap = collectionArticles.stream()
                    .collect(Collectors.toMap(CollectionArticleDO::getArticleId, CollectionArticleDO::getSortOrder));

            List<CollectionDetailRespDTO.CollectionArticleItemDTO> articleItems = Collections.emptyList();
            if (!pagedArticleIds.isEmpty()) {
                List<ArticleDO> articles = articleMapper.selectList(Wrappers.<ArticleDO>lambdaQuery()
                        .in(ArticleDO::getId, pagedArticleIds)
                        .eq(ArticleDO::getDelFlag, 0));

                // 批量查询文章作者名
                List<Long> articleAuthorIds = articles.stream()
                        .map(ArticleDO::getAuthorId)
                        .distinct()
                        .collect(Collectors.toList());
                Map<Long, String> articleAuthorMap = new HashMap<>();
                if (!articleAuthorIds.isEmpty()) {
                    userMapper.selectList(Wrappers.<UserDO>lambdaQuery().in(UserDO::getId, articleAuthorIds))
                            .forEach(u -> articleAuthorMap.put(u.getId(), u.getUsername()));
                }

                articleItems = articles.stream()
                        .map(article -> {
                            CollectionDetailRespDTO.CollectionArticleItemDTO item = new CollectionDetailRespDTO.CollectionArticleItemDTO();
                            item.setId(article.getId());
                            item.setTitle(article.getTitle());
                            item.setSummary(article.getSummary());
                            item.setCoverImage(article.getCoverImage());
                            item.setAuthorName(articleAuthorMap.getOrDefault(article.getAuthorId(), ""));
                            item.setAuthorId(article.getAuthorId());
                            item.setViewCount(article.getViewCount());
                            item.setFavoriteCount(article.getFavoriteCount());
                            item.setReadingTime(article.getReadingTime());
                            item.setSortOrder(sortOrderMap.getOrDefault(article.getId(), 0));
                            item.setCreateTime(article.getCreateTime());
                            return item;
                        })
                        .sorted(Comparator.comparingInt(CollectionDetailRespDTO.CollectionArticleItemDTO::getSortOrder))
                        .collect(Collectors.toList());
            }

            detail.setArticles(articleItems);
        } else {
            detail.setArticles(Collections.emptyList());
        }

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_DETAIL, CACHE_ARTICLES, CACHE_HOME, CACHE_NAV}, allEntries = true)
    public void addArticleToCollection(Long collectionId, Long articleId) {
        CollectionDO collection = getCollectionOrThrow(collectionId);
        checkCollectionOwnership(collection);

        // 检查文章是否存在
        ArticleDO article = articleMapper.selectOne(Wrappers.<ArticleDO>lambdaQuery()
                .eq(ArticleDO::getId, articleId)
                .eq(ArticleDO::getDelFlag, 0));
        if (article == null) {
            throw new ClientException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        // 检查是否已在合集中
        CollectionArticleDO existing = collectionArticleMapper.selectOne(Wrappers.<CollectionArticleDO>lambdaQuery()
                .eq(CollectionArticleDO::getCollectionId, collectionId)
                .eq(CollectionArticleDO::getArticleId, articleId)
                .eq(CollectionArticleDO::getDelFlag, 0));
        if (existing != null) {
            throw new ClientException(ErrorCode.COLLECTION_ARTICLE_ALREADY_EXISTS);
        }

        // 获取当前最大排序值
        Integer maxSortOrder = getMaxSortOrder(collectionId);

        CollectionArticleDO ca = CollectionArticleDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .collectionId(collectionId)
                .articleId(articleId)
                .sortOrder(maxSortOrder + 1)
                .build();
        try {
            collectionArticleMapper.insert(ca);
        } catch (DuplicateKeyException e) {
            throw new ClientException(ErrorCode.COLLECTION_ARTICLE_ALREADY_EXISTS);
        }

        // 更新文章计数
        incrementArticleCount(collectionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_DETAIL, CACHE_ARTICLES, CACHE_HOME, CACHE_NAV}, allEntries = true)
    public void removeArticleFromCollection(Long collectionId, Long articleId) {
        CollectionDO collection = getCollectionOrThrow(collectionId);
        checkCollectionOwnership(collection);

        CollectionArticleDO existing = collectionArticleMapper.selectOne(Wrappers.<CollectionArticleDO>lambdaQuery()
                .eq(CollectionArticleDO::getCollectionId, collectionId)
                .eq(CollectionArticleDO::getArticleId, articleId)
                .eq(CollectionArticleDO::getDelFlag, 0));
        if (existing == null) {
            throw new ClientException(ErrorCode.COLLECTION_ARTICLE_NOT_IN_COLLECTION);
        }

        // 物理删除关联记录，避免唯一索引(collection_id, article_id)冲突导致无法重新添加
        collectionArticleMapper.deleteById(existing.getId());

        // 更新文章计数
        decrementArticleCount(collectionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_DETAIL, CACHE_ARTICLES, CACHE_HOME, CACHE_NAV}, allEntries = true)
    public void batchAddArticles(Long collectionId, CollectionArticleBatchReqDTO requestParam) {
        CollectionDO collection = getCollectionOrThrow(collectionId);
        checkCollectionOwnership(collection);

        if (CollectionUtils.isEmpty(requestParam.getArticleIds())) {
            return;
        }

        // 批量查询已在合集中的文章，避免 N+1 查询
        List<CollectionArticleDO> existingRelations = collectionArticleMapper.selectList(
                Wrappers.<CollectionArticleDO>lambdaQuery()
                        .eq(CollectionArticleDO::getCollectionId, collectionId)
                        .in(CollectionArticleDO::getArticleId, requestParam.getArticleIds())
                        .eq(CollectionArticleDO::getDelFlag, 0));
        Set<Long> existingArticleIds = existingRelations.stream()
                .map(CollectionArticleDO::getArticleId)
                .collect(Collectors.toSet());

        Integer maxSortOrder = getMaxSortOrder(collectionId);
        List<CollectionArticleDO> toInsert = new ArrayList<>();
        int order = maxSortOrder + 1;

        for (Long articleId : requestParam.getArticleIds()) {
            if (existingArticleIds.contains(articleId)) {
                continue;
            }
            toInsert.add(CollectionArticleDO.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .collectionId(collectionId)
                    .articleId(articleId)
                    .sortOrder(order++)
                    .build());
        }

        if (!toInsert.isEmpty()) {
            collectionArticleMapper.batchInsert(toInsert);
            collectionMapper.updateArticleCount(collectionId, toInsert.size());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CACHE_DETAIL, CACHE_ARTICLES, CACHE_NAV}, allEntries = true)
    public void updateArticleSort(Long collectionId, CollectionArticleSortReqDTO requestParam) {
        CollectionDO collection = getCollectionOrThrow(collectionId);
        checkCollectionOwnership(collection);

        if (CollectionUtils.isEmpty(requestParam.getArticleIds())) {
            return;
        }

        // 一次性查询所有相关关联记录，避免 N+1 查询
        List<CollectionArticleDO> existingRelations = collectionArticleMapper.selectList(
                Wrappers.<CollectionArticleDO>lambdaQuery()
                        .eq(CollectionArticleDO::getCollectionId, collectionId)
                        .in(CollectionArticleDO::getArticleId, requestParam.getArticleIds())
                        .eq(CollectionArticleDO::getDelFlag, 0));
        Map<Long, Long> articleIdToRelationId = existingRelations.stream()
                .collect(Collectors.toMap(CollectionArticleDO::getArticleId, CollectionArticleDO::getId));

        // 按新顺序更新 sort_order
        for (int i = 0; i < requestParam.getArticleIds().size(); i++) {
            Long articleId = requestParam.getArticleIds().get(i);
            Long relationId = articleIdToRelationId.get(articleId);
            if (relationId != null) {
                collectionArticleMapper.updateSortOrder(relationId, i);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAV, key = "#articleId")
    public ArticleNavInfoRespDTO getArticleNavigation(Long articleId) {
        ArticleNavInfoRespDTO navInfo = new ArticleNavInfoRespDTO();

        // 查询文章是否属于合集
        CollectionArticleDO ca = collectionArticleMapper.selectOne(Wrappers.<CollectionArticleDO>lambdaQuery()
                .eq(CollectionArticleDO::getArticleId, articleId)
                .eq(CollectionArticleDO::getDelFlag, 0)
                .last("LIMIT 1"));

        if (ca != null) {
            // 文章属于合集
            navInfo.setInCollection(true);
            navInfo.setCollectionId(ca.getCollectionId().toString());

            CollectionDO collection = collectionMapper.selectOne(Wrappers.<CollectionDO>lambdaQuery()
                    .eq(CollectionDO::getId, ca.getCollectionId())
                    .eq(CollectionDO::getDelFlag, 0));
            if (collection != null) {
                navInfo.setCollectionTitle(collection.getTitle());
            }

            // 查询合集中所有文章，按 sort_order 排序
            List<CollectionArticleDO> allArticles = collectionArticleMapper.selectList(
                    Wrappers.<CollectionArticleDO>lambdaQuery()
                            .eq(CollectionArticleDO::getCollectionId, ca.getCollectionId())
                            .eq(CollectionArticleDO::getDelFlag, 0)
                            .orderByAsc(CollectionArticleDO::getSortOrder));

            int currentIndex = -1;
            for (int i = 0; i < allArticles.size(); i++) {
                if (allArticles.get(i).getArticleId().equals(articleId)) {
                    currentIndex = i;
                    break;
                }
            }

            if (currentIndex > 0) {
                Long prevArticleId = allArticles.get(currentIndex - 1).getArticleId();
                ArticleDO prevArticle = articleMapper.selectOne(Wrappers.<ArticleDO>lambdaQuery()
                        .eq(ArticleDO::getId, prevArticleId)
                        .eq(ArticleDO::getDelFlag, 0)
                        .eq(ArticleDO::getPublished, 1));
                if (prevArticle != null && Integer.valueOf(1).equals(prevArticle.getPublished())) {
                    ArticleNavInfoRespDTO.NavArticle prev = new ArticleNavInfoRespDTO.NavArticle();
                    prev.setId(prevArticleId.toString());
                    prev.setTitle(prevArticle.getTitle());
                    navInfo.setPrev(prev);
                }
            }

            if (currentIndex < allArticles.size() - 1) {
                Long nextArticleId = allArticles.get(currentIndex + 1).getArticleId();
                ArticleDO nextArticle = articleMapper.selectOne(Wrappers.<ArticleDO>lambdaQuery()
                        .eq(ArticleDO::getId, nextArticleId)
                        .eq(ArticleDO::getDelFlag, 0)
                        .eq(ArticleDO::getPublished, 1));
                if (nextArticle != null && Integer.valueOf(1).equals(nextArticle.getPublished())) {
                    ArticleNavInfoRespDTO.NavArticle next = new ArticleNavInfoRespDTO.NavArticle();
                    next.setId(nextArticleId.toString());
                    next.setTitle(nextArticle.getTitle());
                    navInfo.setNext(next);
                }
            }
        } else {
            // 文章不属于合集，按时间排序导航
            navInfo.setInCollection(false);
            navInfo.setCollectionId(null);
            navInfo.setCollectionTitle(null);

            ArticleDO currentArticle = articleMapper.selectOne(Wrappers.<ArticleDO>lambdaQuery()
                    .eq(ArticleDO::getId, articleId)
                    .eq(ArticleDO::getDelFlag, 0));
            if (currentArticle == null) {
                return navInfo;
            }

            // 上一篇：创建时间早于当前文章的最近一篇
            ArticleDO prevArticle = articleMapper.selectOne(Wrappers.<ArticleDO>lambdaQuery()
                    .eq(ArticleDO::getDelFlag, 0)
                    .eq(ArticleDO::getPublished, 1)
                    .lt(ArticleDO::getCreateTime, currentArticle.getCreateTime())
                    .orderByDesc(ArticleDO::getCreateTime)
                    .last("LIMIT 1"));
            if (prevArticle != null) {
                ArticleNavInfoRespDTO.NavArticle prev = new ArticleNavInfoRespDTO.NavArticle();
                prev.setId(prevArticle.getId().toString());
                prev.setTitle(prevArticle.getTitle());
                navInfo.setPrev(prev);
            }

            // 下一篇：创建时间晚于当前文章的最早一篇
            ArticleDO nextArticle = articleMapper.selectOne(Wrappers.<ArticleDO>lambdaQuery()
                    .eq(ArticleDO::getDelFlag, 0)
                    .eq(ArticleDO::getPublished, 1)
                    .gt(ArticleDO::getCreateTime, currentArticle.getCreateTime())
                    .orderByAsc(ArticleDO::getCreateTime)
                    .last("LIMIT 1"));
            if (nextArticle != null) {
                ArticleNavInfoRespDTO.NavArticle next = new ArticleNavInfoRespDTO.NavArticle();
                next.setId(nextArticle.getId().toString());
                next.setTitle(nextArticle.getTitle());
                navInfo.setNext(next);
            }
        }

        return navInfo;
    }

    @Override
    public CollectionDO getCollectionByArticleId(Long articleId) {
        CollectionArticleDO ca = collectionArticleMapper.selectOne(Wrappers.<CollectionArticleDO>lambdaQuery()
                .eq(CollectionArticleDO::getArticleId, articleId)
                .eq(CollectionArticleDO::getDelFlag, 0)
                .last("LIMIT 1"));
        if (ca == null) {
            return null;
        }
        return collectionMapper.selectOne(Wrappers.<CollectionDO>lambdaQuery()
                .eq(CollectionDO::getId, ca.getCollectionId())
                .eq(CollectionDO::getDelFlag, 0));
    }

    @Override
    @CacheEvict(value = {CACHE_DETAIL, CACHE_ARTICLES, CACHE_HOME, CACHE_NAV}, allEntries = true)
    public void evictCollectionCache() {
        // 清除合集相关缓存
    }

    private CollectionDO getCollectionOrThrow(Long id) {
        CollectionDO collection = collectionMapper.selectOne(Wrappers.<CollectionDO>lambdaQuery()
                .eq(CollectionDO::getId, id)
                .eq(CollectionDO::getDelFlag, 0));
        if (collection == null) {
            throw new ClientException(ErrorCode.COLLECTION_NOT_FOUND);
        }
        return collection;
    }

    private void checkCollectionOwnership(CollectionDO collection) {
        UserRole currentRole = UserContext.getRole();
        if (UserRole.DEVELOPER.equals(currentRole)) {
            return;
        }

        String currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new ClientException(ErrorCode.COLLECTION_PERMISSION_DENIED);
        }

        if (!currentUserId.equals(collection.getAuthorId().toString())) {
            throw new ClientException(ErrorCode.COLLECTION_PERMISSION_DENIED);
        }
    }

    private Integer getMaxSortOrder(Long collectionId) {
        return collectionArticleMapper.selectMaxSortOrder(collectionId);
    }

    private void incrementArticleCount(Long collectionId) {
        collectionMapper.updateArticleCount(collectionId, 1);
    }

    private void decrementArticleCount(Long collectionId) {
        collectionMapper.updateArticleCount(collectionId, -1);
    }
}
