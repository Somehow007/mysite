package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.context.UserInfoDTO;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.CategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplDeleteTest {

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
    private CategoryMapper categoryMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private static final Long ARTICLE_ID = 123456789L;
    private static final Long AUTHOR_ID = 987654321L;

    @BeforeEach
    void setUp() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId(AUTHOR_ID.toString())
                .role(UserRole.USER)
                .build());

        // ServiceImpl.baseMapper needs to be set via reflection for @InjectMocks
        try {
            var field = ArticleServiceImpl.class.getSuperclass().getDeclaredField("baseMapper");
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

    private ArticleDO createArticleDO(Long id, Long authorId, Integer delFlag) {
        ArticleDO article = new ArticleDO();
        article.setId(id);
        article.setAuthorId(authorId);
        article.setDelFlag(delFlag);
        return article;
    }

    @Test
    void deleteArticle_asOwner_shouldSucceed() {
        ArticleDO article = createArticleDO(ARTICLE_ID, AUTHOR_ID, 0);
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.delete(any())).thenReturn(1);
        when(articleTagMapper.physicalDeleteByArticleId(ARTICLE_ID)).thenReturn(2);
        when(userFavoriteArticleMapper.delete(any())).thenReturn(1);

        assertDoesNotThrow(() -> articleService.deleteArticle(ARTICLE_ID));

        verify(articleMapper).delete(any());
        verify(articleTagMapper).physicalDeleteByArticleId(ARTICLE_ID);
        verify(userFavoriteArticleMapper).delete(any());
        verify(articleSearchService).deleteArticle(ARTICLE_ID);
    }

    @Test
    void deleteArticle_asDeveloper_shouldSucceed() {
        UserContext.removeUser();
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1")
                .role(UserRole.DEVELOPER)
                .build());

        when(articleMapper.delete(any())).thenReturn(1);
        when(articleTagMapper.physicalDeleteByArticleId(ARTICLE_ID)).thenReturn(0);
        when(userFavoriteArticleMapper.delete(any())).thenReturn(0);

        assertDoesNotThrow(() -> articleService.deleteArticle(ARTICLE_ID));

        verify(articleMapper, never()).selectOne(any());
        verify(articleMapper).delete(any());
        verify(articleSearchService).deleteArticle(ARTICLE_ID);
    }

    @Test
    void deleteArticle_notOwner_shouldThrowException() {
        ArticleDO article = createArticleDO(ARTICLE_ID, 999999L, 0);
        when(articleMapper.selectOne(any())).thenReturn(article);

        assertThrows(ClientException.class, () -> articleService.deleteArticle(ARTICLE_ID));

        verify(articleMapper, never()).delete(any());
        verify(articleTagMapper, never()).physicalDeleteByArticleId(anyLong());
        verify(userFavoriteArticleMapper, never()).delete(any());
        verify(articleSearchService, never()).deleteArticle(any());
    }

    @Test
    void deleteArticle_noAuth_shouldThrowException() {
        UserContext.removeUser();

        assertThrows(ClientException.class, () -> articleService.deleteArticle(ARTICLE_ID));

        verify(articleMapper, never()).delete(any());
    }

    @Test
    void deleteArticle_articleNotFound_shouldThrowException() {
        when(articleMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> articleService.deleteArticle(ARTICLE_ID));

        verify(articleMapper, never()).delete(any());
    }

    @Test
    void deleteArticle_alreadyDeleted_shouldThrowException() {
        // 已删除的文章在checkArticleOwnership中查不到（del_flag=0条件），返回null
        when(articleMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> articleService.deleteArticle(ARTICLE_ID));

        verify(articleMapper, never()).delete(any());
    }

    @Test
    void deleteArticle_updateFailed_shouldThrowException() {
        ArticleDO article = createArticleDO(ARTICLE_ID, AUTHOR_ID, 0);
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.delete(any())).thenReturn(0);

        assertThrows(ClientException.class, () -> articleService.deleteArticle(ARTICLE_ID));

        verify(articleTagMapper, never()).physicalDeleteByArticleId(anyLong());
        verify(articleSearchService, never()).deleteArticle(any());
    }

    @Test
    void deleteArticle_shouldCleanupRelatedData() {
        ArticleDO article = createArticleDO(ARTICLE_ID, AUTHOR_ID, 0);
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(articleMapper.delete(any())).thenReturn(1);
        when(articleTagMapper.physicalDeleteByArticleId(ARTICLE_ID)).thenReturn(3);
        when(userFavoriteArticleMapper.delete(any())).thenReturn(2);

        articleService.deleteArticle(ARTICLE_ID);

        verify(articleTagMapper).physicalDeleteByArticleId(ARTICLE_ID);
        verify(userFavoriteArticleMapper).delete(any());
        verify(articleSearchService).deleteArticle(ARTICLE_ID);
    }
}
