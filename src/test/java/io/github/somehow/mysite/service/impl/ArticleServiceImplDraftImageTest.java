package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.CollectionArticleMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.article.ArticleCreateReqDTO;
import io.github.somehow.mysite.dto.req.article.ArticleUpdateReqDTO;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.CategoryService;
import io.github.somehow.mysite.service.CollectionService;
import io.github.somehow.mysite.service.ImageService;
import io.github.somehow.mysite.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 草稿保存也必须转正正文引用的暂存图，使草稿引用图不为孤儿、可保存入系统。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleServiceImpl 草稿保存图片转正")
class ArticleServiceImplDraftImageTest {

    private static final String IMG = "/uploads/images/2026/10/1_draft.png";
    private static final String CONTENT = "草稿正文 ![图](" + IMG + ")";

    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private ArticleSearchService articleSearchService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserFavoriteArticleMapper userFavoriteArticleMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TagService tagService;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ArticleTagMapper articleTagMapper;
    @Mock
    private CollectionService collectionService;
    @Mock
    private CollectionArticleMapper collectionArticleMapper;
    @Mock
    private ArticleViewCountService articleViewCountService;
    @Mock
    private ArticleCacheService articleCacheService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ImageService imageService;

    @InjectMocks
    private ArticleServiceImpl articleService;

    @BeforeEach
    void bindBaseMapper() {
        // LambdaUpdateWrapper 需要实体表元数据缓存
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                io.github.somehow.mysite.dao.entity.ArticleDO.class);
        io.github.somehow.mysite.commons.context.UserContext.setUser(
                io.github.somehow.mysite.commons.context.UserInfoDTO.builder()
                        .userId("1")
                        .role(io.github.somehow.mysite.commons.enums.UserRole.ADMIN)
                        .build());
        try {
            java.lang.reflect.Field field = findField(ArticleServiceImpl.class, "baseMapper");
            field.setAccessible(true);
            field.set(articleService, articleMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        lenient().when(articleMapper.insert(any(io.github.somehow.mysite.dao.entity.ArticleDO.class))).thenReturn(1);
        lenient().when(articleMapper.update(any())).thenReturn(1);
        lenient().when(articleMapper.selectOne(any())).thenReturn(null);
    }

    @org.junit.jupiter.api.AfterEach
    void clearUser() {
        io.github.somehow.mysite.commons.context.UserContext.removeUser();
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

    @Test
    void createDraft_promotesStagingImages() {
        ArticleCreateReqDTO req = new ArticleCreateReqDTO();
        req.setAuthorId("1");
        req.setTitle("草稿标题");
        req.setContent(CONTENT);
        req.setPublished(0);

        articleService.createArticle(req);

        ArgumentCaptor<Collection<String>> captor = argumentCaptorForCollection();
        verify(imageService, atLeastOnce()).promoteStagingImages(captor.capture());
        assertTrue(captor.getValue().contains(IMG), "草稿正文中的暂存图 URL 应参与转正");
    }

    @Test
    void updateDraft_promotesStagingImages() {
        when(articleMapper.selectOne(any())).thenReturn(null);
        when(articleMapper.update(any())).thenReturn(1);

        ArticleUpdateReqDTO req = new ArticleUpdateReqDTO();
        req.setId(99L);
        req.setTitle("草稿标题");
        req.setContent(CONTENT);
        req.setPublished(0);

        articleService.updateArticle(req);

        ArgumentCaptor<Collection<String>> captor = argumentCaptorForCollection();
        verify(imageService, atLeastOnce()).promoteStagingImages(captor.capture());
        assertTrue(captor.getValue().contains(IMG), "草稿正文中的暂存图 URL 应参与转正");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<String>> argumentCaptorForCollection() {
        return ArgumentCaptor.forClass(Collection.class);
    }
}
