package io.github.somehow.mysite.service.impl;

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
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.CategoryService;
import io.github.somehow.mysite.service.CollectionService;
import io.github.somehow.mysite.service.TagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * batchUpdateVisibility 单元测试。
 *
 * 覆盖：
 *   - 正常：同状态多篇批量切换，搜索索引与详情缓存逐篇刷新
 *   - 异常兜底：选中文章混合公开/隐藏 → 拒绝整批，数据库无任何更新
 *   - 权限：非管理员不能改他人文章；管理员可改任意文章
 *   - 参数：文章不存在 / visibility 非法
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleServiceImpl.batchUpdateVisibility")
class ArticleServiceImplVisibilityTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private ArticleTagMapper articleTagMapper;

    @Mock
    private UserFavoriteArticleMapper userFavoriteArticleMapper;

    @Mock
    private ArticleSearchService articleSearchService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TagService tagService;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CollectionService collectionService;

    @Mock
    private CollectionArticleMapper collectionArticleMapper;

    @Mock
    private ArticleCacheService articleCacheService;

    @Mock
    private ArticleViewCountService articleViewCountService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private static final Long AUTHOR_ID = 987654321L;
    private static final Long OTHER_AUTHOR_ID = 111111111L;

    @BeforeEach
    void setUp() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId(AUTHOR_ID.toString())
                .role(UserRole.USER)
                .build());

        // 初始化 MyBatis-Plus TableInfo 缓存：LambdaUpdateWrapper.set() 会立即解析列名，
        // 纯单测环境没有经过 Mapper 初始化流程，必须手动 install，否则抛 "can not find lambda cache"
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                ArticleDO.class);

        // MyBatis-Plus 3.5.14: baseMapper 字段在 CrudRepository（ServiceImpl 的父类）中
        try {
            java.lang.reflect.Field field = findField(ArticleServiceImpl.class, "baseMapper");
            field.setAccessible(true);
            field.set(articleService, articleMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private ArticleDO article(Long id, Long authorId, Integer visibility) {
        ArticleDO a = new ArticleDO();
        a.setId(id);
        a.setAuthorId(authorId);
        a.setVisibility(visibility);
        a.setDelFlag(0);
        return a;
    }

    @Test
    @DisplayName("同为公开的文章批量隐藏：更新成功并逐篇刷新索引与缓存")
    void batchUpdateVisibility_sameVisibility_shouldUpdate() {
        ArticleDO a1 = article(1L, AUTHOR_ID, 0);
        ArticleDO a2 = article(2L, AUTHOR_ID, 0);
        when(articleMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(articleMapper.update(any())).thenReturn(2);

        assertDoesNotThrow(() -> articleService.batchUpdateVisibility(List.of(1L, 2L), 1));

        verify(articleMapper).update(any());
        verify(articleSearchService).updateArticle(a1);
        verify(articleSearchService).updateArticle(a2);
        verify(articleCacheService).evictArticleDetail(1L);
        verify(articleCacheService).evictArticleDetail(2L);
        // 传给搜索索引的对象应已带上目标 visibility
        assertEquals(1, a1.getVisibility());
        assertEquals(1, a2.getVisibility());
    }

    @Test
    @DisplayName("visibility 为 null 的旧数据按公开(0)归一化参与混合判断")
    void batchUpdateVisibility_nullVisibilityNormalized() {
        ArticleDO a1 = article(1L, AUTHOR_ID, null);
        ArticleDO a2 = article(2L, AUTHOR_ID, 0);
        when(articleMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(articleMapper.update(any())).thenReturn(2);

        assertDoesNotThrow(() -> articleService.batchUpdateVisibility(List.of(1L, 2L), 1));

        verify(articleMapper).update(any());
    }

    @Test
    @DisplayName("混合选中公开与隐藏文章：拒绝整批操作，数据库无任何更新")
    void batchUpdateVisibility_mixedVisibility_shouldReject() {
        ArticleDO a1 = article(1L, AUTHOR_ID, 0);
        ArticleDO a2 = article(2L, AUTHOR_ID, 1);
        when(articleMapper.selectList(any())).thenReturn(List.of(a1, a2));

        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.batchUpdateVisibility(List.of(1L, 2L), 1));
        assertEquals(ErrorCode.ARTICLE_VISIBILITY_MIXED.code(), ex.errorCode);

        verify(articleMapper, never()).update(any());
        verify(articleSearchService, never()).updateArticle(any());
    }

    @Test
    @DisplayName("非管理员操作他人文章：权限拒绝")
    void batchUpdateVisibility_notOwner_shouldReject() {
        ArticleDO a1 = article(1L, AUTHOR_ID, 0);
        ArticleDO a2 = article(2L, OTHER_AUTHOR_ID, 0);
        when(articleMapper.selectList(any())).thenReturn(List.of(a1, a2));

        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.batchUpdateVisibility(List.of(1L, 2L), 1));
        assertEquals(ErrorCode.ARTICLE_PERMISSION_DENIED.code(), ex.errorCode);

        verify(articleMapper, never()).update(any());
    }

    @Test
    @DisplayName("管理员可批量修改他人文章可见性")
    void batchUpdateVisibility_asAdmin_shouldSucceed() {
        UserContext.removeUser();
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1")
                .role(UserRole.ADMIN)
                .build());

        ArticleDO a1 = article(1L, OTHER_AUTHOR_ID, 1);
        when(articleMapper.selectList(any())).thenReturn(List.of(a1));
        when(articleMapper.update(any())).thenReturn(1);

        assertDoesNotThrow(() -> articleService.batchUpdateVisibility(List.of(1L), 0));

        verify(articleMapper).update(any());
        verify(articleSearchService).updateArticle(a1);
    }

    @Test
    @DisplayName("部分文章不存在：整批拒绝")
    void batchUpdateVisibility_articleMissing_shouldReject() {
        when(articleMapper.selectList(any())).thenReturn(List.of(article(1L, AUTHOR_ID, 0)));

        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.batchUpdateVisibility(List.of(1L, 2L), 1));
        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.code(), ex.errorCode);

        verify(articleMapper, never()).update(any());
    }

    @Test
    @DisplayName("visibility 取值非法：参数错误")
    void batchUpdateVisibility_invalidVisibility_shouldReject() {
        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.batchUpdateVisibility(List.of(1L), 2));
        assertEquals(ErrorCode.ARTICLE_PARAM_REQUIRED.code(), ex.errorCode);

        verify(articleMapper, never()).update(any());
    }
}
