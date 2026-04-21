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
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.*;
import io.github.somehow.mysite.dao.mapper.*;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArchiveRespDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.elasticsearch.ArticleDocument;
import io.github.somehow.mysite.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
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
    private final ArticleEsRepository esRepository;
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

        ArticleDocument doc = ArticleDocument.builder()
                .id(articleDO.getId().toString())
                .title(requestParam.getTitle())
                .content(requestParam.getContent())
                .summary(requestParam.getSummary())
                .authorId(articleDO.getAuthorId().toString())
                .categoryId(requestParam.getCategoryId() != null ? requestParam.getCategoryId().toString() : null)
                .createTime(articleDO.getCreateTime())
                .build();
        esRepository.save(doc);
    }

    @Override
    @Transactional
    public void updateArticle(ArticleUpdateReqDTO requestParam) {
        if (Objects.isNull(requestParam)) {
            throw new ClientException("更新失败，未传递更新参数");
        }

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
            ArticleDocument articleDocument = ArticleDocument.builder()
                    .id(updatedArticle.getId().toString())
                    .title(updatedArticle.getTitle())
                    .content(updatedArticle.getContent())
                    .summary(updatedArticle.getSummary())
                    .authorId(updatedArticle.getAuthorId().toString())
                    .categoryId(updatedArticle.getCategoryId() != null ? updatedArticle.getCategoryId().toString() : null)
                    .createTime(updatedArticle.getCreateTime())
                    .build();
            esRepository.save(articleDocument);
        }
    }

    @Override
    public void deleteArticle(Long id) {
        LambdaUpdateWrapper<ArticleDO> updateWrapper = Wrappers.lambdaUpdate(ArticleDO.class)
                .eq(ArticleDO::getId, id)
                .eq(ArticleDO::getDelFlag, 0);
        ArticleDO articleDO = new ArticleDO();
        articleDO.setDelFlag(1);

        int rows = baseMapper.update(articleDO, updateWrapper);
        if (rows <= 0) {
            throw new ClientException("删除文章失败，文章不存在");
        }

        esRepository.deleteById(id.toString());
    }

    @Override
    public IPage<ArticlePageQueryRespDTO> pageQueryArticle(ArticlePageQueryReqDTO requestParam) {
        PageRequest pageRequest = PageRequest.of(
                (int) (requestParam.getCurrent() - 1),
                (int) requestParam.getSize());

        String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "");
        String searchType = StrUtil.blankToDefault(requestParam.getSearchType(), "title");
        String categorySlug = requestParam.getCategorySlug();
        String tagSlug = requestParam.getTagSlug();

        if (StrUtil.isNotBlank(tagSlug)) {
            return pageQueryByTagSlug(tagSlug, keyword, searchType, requestParam, pageRequest);
        }

        if (StrUtil.isNotBlank(categorySlug)) {
            return pageQueryByCategorySlug(categorySlug, keyword, searchType, requestParam, pageRequest);
        }

        org.springframework.data.domain.Page<ArticleDocument> esPage;

        if (StrUtil.isBlank(keyword)) {
            esPage = esRepository.findAll(pageRequest);
        } else {
            keyword = keyword.toLowerCase();
            switch (searchType) {
                case "content":
                    esPage = esRepository.findByContentContaining(keyword, pageRequest);
                    break;
                case "author":
                    List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                    if (!CollectionUtils.isEmpty(matchedUsers)) {
                        List<String> authorIds = matchedUsers.stream()
                                .map(user -> user.getId().toString())
                                .collect(Collectors.toList());
                        esPage = esRepository.findByAuthorIdIn(authorIds, pageRequest);
                    } else {
                        return new Page<>();
                    }
                    break;
                default:
                    esPage = esRepository.findByTitleContaining(keyword, pageRequest);
                    break;
            }
        }

        return buildArticlePageResult(esPage, requestParam);
    }

    private IPage<ArticlePageQueryRespDTO> pageQueryByCategorySlug(String categorySlug, String keyword, String searchType,
                                                                     ArticlePageQueryReqDTO requestParam, PageRequest pageRequest) {
        CategoryDO category = categoryMapper.selectOne(Wrappers.<CategoryDO>lambdaQuery()
                .eq(CategoryDO::getSlug, categorySlug));
        if (category == null) {
            return new Page<>();
        }
        String categoryId = category.getId().toString();

        org.springframework.data.domain.Page<ArticleDocument> esPage;
        if (StrUtil.isBlank(keyword)) {
            esPage = esRepository.findByCategoryId(categoryId, pageRequest);
        } else {
            keyword = keyword.toLowerCase();
            switch (searchType) {
                case "content":
                    esPage = esRepository.findByCategoryIdAndContentContaining(categoryId, keyword, pageRequest);
                    break;
                case "author":
                    List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                    if (!CollectionUtils.isEmpty(matchedUsers)) {
                        List<String> authorIds = matchedUsers.stream()
                                .map(user -> user.getId().toString())
                                .collect(Collectors.toList());
                        esPage = esRepository.findByCategoryIdAndAuthorIdIn(categoryId, authorIds, pageRequest);
                    } else {
                        return new Page<>();
                    }
                    break;
                default:
                    esPage = esRepository.findByCategoryIdAndTitleContaining(categoryId, keyword, pageRequest);
                    break;
            }
        }
        return buildArticlePageResult(esPage, requestParam);
    }

    private IPage<ArticlePageQueryRespDTO> pageQueryByTagSlug(String tagSlug, String keyword, String searchType,
                                                                ArticlePageQueryReqDTO requestParam, PageRequest pageRequest) {
        TagDO tag = tagMapper.selectOne(Wrappers.<TagDO>lambdaQuery()
                .eq(TagDO::getSlug, tagSlug));
        if (tag == null) {
            return new Page<>();
        }
        List<ArticleTagDO> articleTags = articleTagMapper.selectList(Wrappers.<ArticleTagDO>lambdaQuery()
                .eq(ArticleTagDO::getTagId, tag.getId())
                .eq(ArticleTagDO::getDelFlag, 0));
        if (CollectionUtils.isEmpty(articleTags)) {
            return new Page<>();
        }
        List<String> articleIds = articleTags.stream()
                .map(at -> at.getArticleId().toString())
                .collect(Collectors.toList());

        org.springframework.data.domain.Page<ArticleDocument> esPage;
        if (StrUtil.isBlank(keyword)) {
            esPage = esRepository.findByIdIn(articleIds, pageRequest);
        } else {
            keyword = keyword.toLowerCase();
            switch (searchType) {
                case "content":
                    esPage = esRepository.findByIdInAndContentContaining(articleIds, keyword, pageRequest);
                    break;
                case "author":
                    List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                    if (!CollectionUtils.isEmpty(matchedUsers)) {
                        List<String> authorIds = matchedUsers.stream()
                                .map(user -> user.getId().toString())
                                .collect(Collectors.toList());
                        esPage = esRepository.findByIdInAndAuthorIdIn(articleIds, authorIds, pageRequest);
                    } else {
                        return new Page<>();
                    }
                    break;
                default:
                    esPage = esRepository.findByIdInAndTitleContaining(articleIds, keyword, pageRequest);
                    break;
            }
        }
        return buildArticlePageResult(esPage, requestParam);
    }

    private IPage<ArticlePageQueryRespDTO> buildArticlePageResult(org.springframework.data.domain.Page<ArticleDocument> esPage,
                                                                    ArticlePageQueryReqDTO requestParam) {
        if (esPage.hasContent()) {
            List<Long> articleIds = esPage.getContent().stream()
                    .map(doc -> Long.valueOf(doc.getId()))
                    .collect(Collectors.toList());

            List<ArticleDO> articles = baseMapper.selectList(Wrappers.<ArticleDO>lambdaQuery()
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

            IPage<ArticlePageQueryRespDTO> result = new Page<>(requestParam.getCurrent(), requestParam.getSize(), esPage.getTotalElements());
            result.setRecords(records);
            return result;
        } else {
            return new Page<>();
        }
    }

    @Override
    public IPage<ArticlePageQueryRespDTO> pageQueryFavoriteArticle(ArticleFavoritePageQueryReqDTO requestParam) {
        return baseMapper.pageFavoriteArticleResults(requestParam);
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
            throw new ClientException("收藏相关操作失败，未传入正确参数");
        }
        UserFavoriteArticleDO isExist = userFavoriteArticleMapper.selectOne(Wrappers.lambdaQuery(UserFavoriteArticleDO.class)
                .eq(UserFavoriteArticleDO::getArticleId, requestParam.getArticleId())
                .eq(UserFavoriteArticleDO::getUserId, requestParam.getUserId()));
        if (Objects.isNull(isExist)) {
            UserFavoriteArticleDO userFavoriteArticleDO = BeanUtil.toBean(requestParam, UserFavoriteArticleDO.class);
            userFavoriteArticleDO.setId(IdUtil.getSnowflakeNextId());
            try {
                userFavoriteArticleMapper.insert(userFavoriteArticleDO);
                articleMapper.incrementFavoriteCount(Long.parseLong(requestParam.getArticleId()), 1);
            } catch (DuplicateKeyException e) {
                throw new ClientException("已经收藏过了噢～");
            }
        } else if (isExist.getDelFlag() == 1) {
            userFavoriteArticleMapper.update(Wrappers.lambdaUpdate(UserFavoriteArticleDO.class)
                    .eq(UserFavoriteArticleDO::getArticleId, requestParam.getArticleId())
                    .eq(UserFavoriteArticleDO::getUserId, requestParam.getUserId())
                    .eq(UserFavoriteArticleDO::getDelFlag, 1)
                    .set(UserFavoriteArticleDO::getDelFlag, 0));
            articleMapper.incrementFavoriteCount(Long.parseLong(requestParam.getArticleId()), 1);
        } else if (isExist.getDelFlag() == 0) {
            userFavoriteArticleMapper.update(Wrappers.lambdaUpdate(UserFavoriteArticleDO.class)
                    .eq(UserFavoriteArticleDO::getArticleId, requestParam.getArticleId())
                    .eq(UserFavoriteArticleDO::getUserId, requestParam.getUserId())
                    .eq(UserFavoriteArticleDO::getDelFlag, 0)
                    .set(UserFavoriteArticleDO::getDelFlag, 1));
            articleMapper.decrementFavoriteCount(Long.parseLong(requestParam.getArticleId()), 1);
        }
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
}
