package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.context.UserInfoDTO;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.ArticleTagDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.CollectionArticleMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.article.ArticleCreateReqDTO;
import io.github.somehow.mysite.dto.req.article.ArticleUpdateReqDTO;
import io.github.somehow.mysite.ragent.ingestion.ArticleCreatedEvent;
import io.github.somehow.mysite.ragent.ingestion.ArticleUpdatedEvent;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.CategoryService;
import io.github.somehow.mysite.service.CollectionService;
import io.github.somehow.mysite.service.ImageService;
import io.github.somehow.mysite.service.TagService;
import io.github.somehow.mysite.support.TestTableInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文章创建/更新写路径 characterization tests。
 *
 * <p>钉住现状行为（非评判对错）：参数校验顺序、字段默认值、标签/合集关联、
 * 搜索索引与缓存驱逐、暂存图转正、RAG 事件发布。改动这些行为前先跑本类。</p>
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceImplWriteTest {

    @Mock private ArticleMapper articleMapper;
    @Mock private ArticleTagMapper articleTagMapper;
    @Mock private UserFavoriteArticleMapper userFavoriteArticleMapper;
    @Mock private ArticleSearchService articleSearchService;
    @Mock private CategoryService categoryService;
    @Mock private TagService tagService;
    @Mock private CategoryMapper categoryMapper;
    @Mock private TagMapper tagMapper;
    @Mock private UserMapper userMapper;
    @Mock private CollectionService collectionService;
    @Mock private CollectionArticleMapper collectionArticleMapper;
    @Mock private ArticleCacheService articleCacheService;
    @Mock private ArticleViewCountService articleViewCountService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ImageService imageService;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private static final Long ARTICLE_ID = 123456789L;
    private static final Long AUTHOR_ID = 987654321L;

    @BeforeAll
    static void initTableInfo() {
        TestTableInfo.initAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        UserContext.setUser(UserInfoDTO.builder()
                .userId(AUTHOR_ID.toString())
                .role(UserRole.USER)
                .build());
        // baseMapper 在 ServiceImpl 父类中，需反射注入（同 ArticleServiceImplDeleteTest）
        java.lang.reflect.Field field = findField(ArticleServiceImpl.class, "baseMapper");
        field.setAccessible(true);
        field.set(articleService, articleMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException(new NoSuchFieldException(fieldName));
    }

    private ArticleCreateReqDTO createReq() {
        ArticleCreateReqDTO req = new ArticleCreateReqDTO();
        req.setAuthorId(AUTHOR_ID.toString());
        req.setTitle("测试文章");
        req.setContent("# 标题\n\n正文内容");
        return req;
    }

    private ArticleUpdateReqDTO updateReq(Long id) {
        ArticleUpdateReqDTO req = new ArticleUpdateReqDTO();
        req.setId(id);
        return req;
    }

    private ArticleDO existingArticle() {
        ArticleDO article = new ArticleDO();
        article.setId(ARTICLE_ID);
        article.setAuthorId(AUTHOR_ID);
        article.setDelFlag(0);
        article.setContent("旧内容 /uploads/images/old.png");
        return article;
    }

    // ─────────────────────────── createArticle ───────────────────────────

    @Test
    void createArticle_nullParam_shouldThrowParamRequired() {
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.createArticle(null));
        assertEquals(ErrorCode.ARTICLE_PARAM_REQUIRED.code(), ex.getErrorCode());
    }

    @Test
    void createArticle_blankAuthorId_shouldThrowAuthorRequired() {
        ArticleCreateReqDTO req = createReq();
        req.setAuthorId("  ");
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.createArticle(req));
        assertEquals(ErrorCode.ARTICLE_AUTHOR_REQUIRED.code(), ex.getErrorCode());
    }

    @Test
    void createArticle_blankTitle_shouldThrowTitleRequired() {
        ArticleCreateReqDTO req = createReq();
        req.setTitle("");
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.createArticle(req));
        assertEquals(ErrorCode.ARTICLE_TITLE_REQUIRED.code(), ex.getErrorCode());
    }

    @Test
    void createArticle_blankContent_shouldThrowContentRequired() {
        ArticleCreateReqDTO req = createReq();
        req.setContent(null);
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.createArticle(req));
        assertEquals(ErrorCode.ARTICLE_CONTENT_REQUIRED.code(), ex.getErrorCode());
    }

    @Test
    void createArticle_success_shouldApplyDefaultsAndSideEffects() {
        ArticleCreateReqDTO req = createReq();
        req.setTagIds(List.of(11L, 22L));
        req.setCollectionId(5L);
        req.setContent("正文 /uploads/images/pic.png");

        articleService.createArticle(req);

        ArgumentCaptor<ArticleDO> captor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleMapper).insert(captor.capture());
        ArticleDO saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals(AUTHOR_ID, saved.getAuthorId());
        assertEquals(1, saved.getPublished());      // 默认已发布
        assertEquals(0, saved.getVisibility());      // 默认公开
        assertEquals(0, saved.getViewCount());
        assertEquals(0, saved.getFavoriteCount());
        assertNotNull(saved.getReadingTime());

        ArgumentCaptor<List<ArticleTagDO>> tagCaptor = ArgumentCaptor.forClass(List.class);
        verify(articleTagMapper).batchInsert(tagCaptor.capture());
        assertEquals(2, tagCaptor.getValue().size());
        assertTrue(tagCaptor.getValue().stream().allMatch(t -> saved.getId().equals(t.getArticleId())));

        verify(collectionService).addArticleToCollection(5L, saved.getId());
        verify(articleSearchService).indexArticle(saved);
        verify(categoryService).evictCategoryCache();
        verify(tagService).evictTagCache();
        verify(imageService).promoteStagingImages(argThat(c ->
                c != null && c.stream().anyMatch(u -> u.contains("/uploads/images/pic.png"))));
        verify(eventPublisher).publishEvent(argThat(e -> e instanceof ArticleCreatedEvent));
    }

    @Test
    void createArticle_collectionJoinFails_shouldStillSucceed() {
        ArticleCreateReqDTO req = createReq();
        req.setCollectionId(5L);
        doThrow(new ClientException("合集不存在", ErrorCode.COLLECTION_NOT_FOUND))
                .when(collectionService).addArticleToCollection(anyLong(), anyLong());

        assertDoesNotThrow(() -> articleService.createArticle(req));
        verify(articleMapper).insert(any(ArticleDO.class));
        verify(eventPublisher).publishEvent(any(ArticleCreatedEvent.class));
    }

    @Test
    void createArticle_noTagsNoCollection_shouldSkipAssociations() {
        articleService.createArticle(createReq());

        verify(articleTagMapper, never()).batchInsert(anyList());
        verify(collectionService, never()).addArticleToCollection(anyLong(), anyLong());
        verify(imageService).promoteStagingImages(anyCollection());
        verify(eventPublisher).publishEvent(any(ArticleCreatedEvent.class));
    }

    // ─────────────────────────── updateArticle ───────────────────────────

    @Test
    void updateArticle_nullParam_shouldThrowParamRequired() {
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticle(null));
        assertEquals(ErrorCode.ARTICLE_PARAM_REQUIRED.code(), ex.getErrorCode());
    }

    @Test
    void updateArticle_notLoggedIn_shouldThrowOwnershipVerifyFailed() {
        UserContext.removeUser();
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticle(updateReq(ARTICLE_ID)));
        assertEquals(ErrorCode.ARTICLE_OWNERSHIP_VERIFY_FAILED.code(), ex.getErrorCode());
    }

    @Test
    void updateArticle_articleNotFound_shouldThrowNotFound() {
        when(articleMapper.selectOne(any())).thenReturn(null);
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticle(updateReq(ARTICLE_ID)));
        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.code(), ex.getErrorCode());
    }

    @Test
    void updateArticle_notOwner_shouldThrowPermissionDenied() {
        ArticleDO other = existingArticle();
        other.setAuthorId(999L);
        when(articleMapper.selectOne(any())).thenReturn(other);

        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticle(updateReq(ARTICLE_ID)));
        assertEquals(ErrorCode.ARTICLE_PERMISSION_DENIED.code(), ex.getErrorCode());
    }

    @Test
    void updateArticle_adminBypassOwnership_shouldSucceed() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1").role(UserRole.ADMIN).build());
        ArticleDO article = existingArticle();
        // ADMIN 跳过归属校验的 selectOne，只有更新后回查一次
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.update(any())).thenReturn(1);

        ArticleUpdateReqDTO req = updateReq(ARTICLE_ID);
        req.setTitle("新标题");
        articleService.updateArticle(req);

        verify(articleMapper).update(any());
        verify(articleSearchService).updateArticle(article);
        verify(eventPublisher).publishEvent(any(ArticleUpdatedEvent.class));
        verify(articleMapper, times(1)).selectOne(any()); // 仅回查，未做归属校验
    }

    @Test
    void updateArticle_success_nullTagIds_shouldKeepTags() {
        ArticleDO article = existingArticle();
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.update(any())).thenReturn(1);

        ArticleUpdateReqDTO req = updateReq(ARTICLE_ID);
        req.setTitle("新标题");
        req.setTagIds(null);
        articleService.updateArticle(req);

        verify(articleTagMapper, never()).physicalDeleteByArticleId(anyLong());
        verify(articleTagMapper, never()).batchInsert(anyList());
        verify(articleSearchService).updateArticle(article);
        verify(eventPublisher).publishEvent(any(ArticleUpdatedEvent.class));
    }

    @Test
    void updateArticle_emptyTagIds_shouldClearTags() {
        ArticleDO article = existingArticle();
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.update(any())).thenReturn(1);

        ArticleUpdateReqDTO req = updateReq(ARTICLE_ID);
        req.setTagIds(List.of());
        articleService.updateArticle(req);

        verify(articleTagMapper).physicalDeleteByArticleId(ARTICLE_ID);
        verify(articleTagMapper, never()).batchInsert(anyList());
    }

    @Test
    void updateArticle_nonEmptyTagIds_shouldReplaceTags() {
        ArticleDO article = existingArticle();
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.update(any())).thenReturn(1);

        ArticleUpdateReqDTO req = updateReq(ARTICLE_ID);
        req.setTagIds(List.of(7L, 8L));
        articleService.updateArticle(req);

        verify(articleTagMapper).physicalDeleteByArticleId(ARTICLE_ID);
        ArgumentCaptor<List<ArticleTagDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(articleTagMapper).batchInsert(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(t -> ARTICLE_ID.equals(t.getArticleId())));
    }

    @Test
    void updateArticle_updateReturnsZero_shouldThrowUpdateFailed() {
        ArticleDO article = existingArticle();
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.update(any())).thenReturn(0);

        ArticleUpdateReqDTO req = updateReq(ARTICLE_ID);
        req.setTitle("新标题");
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticle(req));
        assertEquals(ErrorCode.ARTICLE_UPDATE_FAILED.code(), ex.getErrorCode());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateArticle_articleMissingAfterUpdate_shouldSkipSearchAndEvent() {
        ArticleDO article = existingArticle();
        // 第一次 selectOne（归属校验）返回文章；第二次（更新后回查）返回 null
        when(articleMapper.selectOne(any())).thenReturn(article).thenReturn(null);
        when(articleMapper.update(any())).thenReturn(1);

        ArticleUpdateReqDTO req = updateReq(ARTICLE_ID);
        req.setTitle("新标题");
        articleService.updateArticle(req);

        verify(articleSearchService, never()).updateArticle(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateArticle_noContentInRequest_shouldPromoteFromDbContent() {
        ArticleDO article = existingArticle(); // content 含 /uploads/images/old.png
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.update(any())).thenReturn(1);

        // 请求不带 content/coverImage → 回退库内正文做暂存图转正
        articleService.updateArticle(updateReq(ARTICLE_ID));

        verify(imageService).promoteStagingImages(argThat(c ->
                c != null && c.stream().anyMatch(u -> u.contains("/uploads/images/old.png"))));
    }
}
