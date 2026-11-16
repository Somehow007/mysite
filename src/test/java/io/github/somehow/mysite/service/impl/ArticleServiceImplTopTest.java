package io.github.somehow.mysite.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.context.UserInfoDTO;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.CollectionArticleMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * updateArticleTop（文章置顶）characterization tests。
 *
 * <p>钉住：参数校验、归属权限（ADMIN/作者本人）、置顶/取消的字段写入、
 * article_detail 缓存驱逐、以及「不发 RAG 事件 / 不碰搜索索引」的刻意边界。</p>
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTopTest {

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

    private ArticleDO existingArticle(Long authorId) {
        ArticleDO article = new ArticleDO();
        article.setId(ARTICLE_ID);
        article.setAuthorId(authorId);
        article.setDelFlag(0);
        return article;
    }

    // ─────────────────────────── 参数校验 ───────────────────────────

    @Test
    void updateArticleTop_null_shouldThrowParamRequired() {
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticleTop(ARTICLE_ID, null));
        assertEquals(ErrorCode.ARTICLE_PARAM_REQUIRED.code(), ex.getErrorCode());
        verify(articleMapper, never()).update(any());
    }

    @Test
    void updateArticleTop_invalidValue_shouldThrowParamRequired() {
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticleTop(ARTICLE_ID, 2));
        assertEquals(ErrorCode.ARTICLE_PARAM_REQUIRED.code(), ex.getErrorCode());
        verify(articleMapper, never()).update(any());
    }

    // ─────────────────────────── 权限 ───────────────────────────

    @Test
    void updateArticleTop_notLoggedIn_shouldThrowOwnershipVerifyFailed() {
        UserContext.removeUser();
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticleTop(ARTICLE_ID, 1));
        assertEquals(ErrorCode.ARTICLE_OWNERSHIP_VERIFY_FAILED.code(), ex.getErrorCode());
    }

    @Test
    void updateArticleTop_articleNotFound_shouldThrowNotFound() {
        when(articleMapper.selectOne(any())).thenReturn(null);
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticleTop(ARTICLE_ID, 1));
        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.code(), ex.getErrorCode());
    }

    @Test
    void updateArticleTop_notOwner_shouldThrowPermissionDenied() {
        when(articleMapper.selectOne(any())).thenReturn(existingArticle(999L));
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticleTop(ARTICLE_ID, 1));
        assertEquals(ErrorCode.ARTICLE_PERMISSION_DENIED.code(), ex.getErrorCode());
    }

    @Test
    void updateArticleTop_adminBypassOwnership_shouldSucceed() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1").role(UserRole.ADMIN).build());
        when(articleMapper.update(any())).thenReturn(1);

        articleService.updateArticleTop(ARTICLE_ID, 1);

        verify(articleMapper).update(any());
        verify(articleCacheService).evictArticleDetail(ARTICLE_ID);
        verify(articleMapper, never()).selectOne(any()); // ADMIN 跳过归属校验
    }

    // ─────────────────────────── 写入与副作用 ───────────────────────────

    @Test
    void updateArticleTop_ownerPin_shouldSetIsTopAndTopTime() {
        when(articleMapper.selectOne(any())).thenReturn(existingArticle(AUTHOR_ID));
        when(articleMapper.update(any())).thenReturn(1);

        articleService.updateArticleTop(ARTICLE_ID, 1);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).update(captor.capture());
        String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.Update<?, ?>) captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("is_top"), "SET 应包含 is_top: " + sqlSet);
        assertTrue(sqlSet.contains("top_time"), "SET 应包含 top_time: " + sqlSet);
        verify(articleCacheService).evictArticleDetail(ARTICLE_ID);
        // 刻意边界：置顶不触发搜索索引更新与 RAG 重向量化
        verify(articleSearchService, never()).updateArticle(any());
        verify(eventPublisher, never()).publishEvent(any(ArticleCreatedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(ArticleUpdatedEvent.class));
    }

    @Test
    void updateArticleTop_ownerUnpin_shouldClearTopTime() {
        when(articleMapper.selectOne(any())).thenReturn(existingArticle(AUTHOR_ID));
        when(articleMapper.update(any())).thenReturn(1);

        articleService.updateArticleTop(ARTICLE_ID, 0);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).update(captor.capture());
        String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.Update<?, ?>) captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("is_top") && sqlSet.contains("top_time"), "SET 应同时写 is_top/top_time: " + sqlSet);
        verify(articleCacheService).evictArticleDetail(ARTICLE_ID);
    }

    @Test
    void updateArticleTop_updateReturnsZero_shouldThrowUpdateFailed() {
        when(articleMapper.selectOne(any())).thenReturn(existingArticle(AUTHOR_ID));
        when(articleMapper.update(any())).thenReturn(0);

        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.updateArticleTop(ARTICLE_ID, 1));
        assertEquals(ErrorCode.ARTICLE_UPDATE_FAILED.code(), ex.getErrorCode());
        verify(articleCacheService, never()).evictArticleDetail(anyLong());
    }
}
