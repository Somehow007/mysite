package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.article.ArticleFavoriteReqDTO;
import io.github.somehow.mysite.dto.resp.ArticleFavoriteRespDTO;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.CategoryService;
import io.github.somehow.mysite.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplFavoriteTest {

    @Mock
    private UserFavoriteArticleMapper userFavoriteArticleMapper;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TagService tagService;

    @Mock
    private ArticleTagMapper articleTagMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ArticleSearchService articleSearchService;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private ArticleFavoriteReqDTO favoriteReqDTO;
    private ArticleDO articleDO;

    private static final Long ARTICLE_ID = 123456789L;
    private static final Long USER_ID = 987654321L;

    @BeforeEach
    void setUp() {
        favoriteReqDTO = new ArticleFavoriteReqDTO();
        favoriteReqDTO.setArticleId(ARTICLE_ID.toString());
        favoriteReqDTO.setUserId(USER_ID.toString());

        articleDO = new ArticleDO();
        articleDO.setId(ARTICLE_ID);
        articleDO.setFavoriteCount(5);
        articleDO.setDelFlag(0);
    }

    private UserFavoriteArticleDO createFavoriteDO(Long id, Long articleId, Long userId, Integer delFlag) {
        UserFavoriteArticleDO doobj = new UserFavoriteArticleDO();
        doobj.setId(id);
        doobj.setArticleId(articleId);
        doobj.setUserId(userId);
        doobj.setDelFlag(delFlag);
        doobj.setCreateTime(new Date());
        doobj.setUpdateTime(new Date());
        return doobj;
    }

    @Test
    void favoriteArticle_newFavorite_shouldInsert() {
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong())).thenReturn(null);
        when(userFavoriteArticleMapper.insert(any(UserFavoriteArticleDO.class))).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper).selectByUserAndArticle(anyLong(), anyLong());
        verify(userFavoriteArticleMapper).insert(any(UserFavoriteArticleDO.class));
        verify(articleMapper).incrementFavoriteCount(ARTICLE_ID, 1);

        assertTrue(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_existingFavorite_shouldCancel() {
        UserFavoriteArticleDO existing = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 0);
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong())).thenReturn(existing);
        when(userFavoriteArticleMapper.softDeleteById(anyLong())).thenReturn(1);
        when(articleMapper.decrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper).selectByUserAndArticle(anyLong(), anyLong());
        verify(userFavoriteArticleMapper).softDeleteById(1L);
        verify(articleMapper).decrementFavoriteCount(ARTICLE_ID, 1);

        assertFalse(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_canceledFavorite_shouldRestore() {
        UserFavoriteArticleDO existing = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 1);
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong())).thenReturn(existing);
        when(userFavoriteArticleMapper.softRestoreById(anyLong())).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper).selectByUserAndArticle(anyLong(), anyLong());
        verify(userFavoriteArticleMapper).softRestoreById(1L);
        verify(articleMapper).incrementFavoriteCount(ARTICLE_ID, 1);

        assertTrue(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_blankParams_shouldThrowException() {
        favoriteReqDTO.setArticleId("");
        favoriteReqDTO.setUserId("");

        assertThrows(ClientException.class, () -> articleService.favoriteArticle(favoriteReqDTO));
    }

    @Test
    void favoriteArticle_nonExistentArticle_shouldThrowException() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(null);

        assertThrows(ClientException.class, () -> articleService.favoriteArticle(favoriteReqDTO));

        verify(userFavoriteArticleMapper, never()).selectByUserAndArticle(anyLong(), anyLong());
    }

    @Test
    void favoriteArticle_deletedArticle_shouldThrowException() {
        ArticleDO deletedArticle = new ArticleDO();
        deletedArticle.setId(ARTICLE_ID);
        deletedArticle.setDelFlag(1);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(deletedArticle);

        assertThrows(ClientException.class, () -> articleService.favoriteArticle(favoriteReqDTO));

        verify(userFavoriteArticleMapper, never()).selectByUserAndArticle(anyLong(), anyLong());
    }

    @Test
    void favoriteArticle_duplicateKey_restoreDeletedRecord() {
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong())).thenReturn(null);
        when(userFavoriteArticleMapper.insert(any(UserFavoriteArticleDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        UserFavoriteArticleDO duplicate = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 1);
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong()))
                .thenReturn(null)
                .thenReturn(duplicate);
        when(userFavoriteArticleMapper.softRestoreById(anyLong())).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper, times(2)).selectByUserAndArticle(anyLong(), anyLong());
        verify(userFavoriteArticleMapper).insert(any(UserFavoriteArticleDO.class));
        verify(userFavoriteArticleMapper).softRestoreById(1L);
        verify(articleMapper).incrementFavoriteCount(ARTICLE_ID, 1);

        assertTrue(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_duplicateKey_returnSuccessWhenAlreadyActive() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);
        when(userFavoriteArticleMapper.insert(any(UserFavoriteArticleDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        UserFavoriteArticleDO duplicate = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 0);
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong()))
                .thenReturn(null)
                .thenReturn(duplicate);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        assertTrue(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_cancelAndReFavorite_fullCycle() {
        UserFavoriteArticleDO active = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 0);
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong())).thenReturn(active);
        when(userFavoriteArticleMapper.softDeleteById(anyLong())).thenReturn(1);
        when(articleMapper.decrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);

        ArticleFavoriteRespDTO result1 = articleService.favoriteArticle(favoriteReqDTO);
        assertFalse(result1.getFavorited());

        UserFavoriteArticleDO inactive = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 1);
        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong())).thenReturn(inactive);
        when(userFavoriteArticleMapper.softRestoreById(anyLong())).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);

        ArticleFavoriteRespDTO result2 = articleService.favoriteArticle(favoriteReqDTO);
        assertTrue(result2.getFavorited());
    }

    @Test
    void favoriteArticle_rapidToggle_multipleTimes() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);
        when(userFavoriteArticleMapper.softDeleteById(anyLong())).thenReturn(1);
        when(userFavoriteArticleMapper.softRestoreById(anyLong())).thenReturn(1);
        when(articleMapper.decrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);

        for (int i = 0; i < 5; i++) {
            UserFavoriteArticleDO active = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 0);
            UserFavoriteArticleDO inactive = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 1);

            when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong()))
                    .thenReturn(active)
                    .thenReturn(inactive);

            ArticleFavoriteRespDTO cancelResult = articleService.favoriteArticle(favoriteReqDTO);
            assertFalse(cancelResult.getFavorited());

            ArticleFavoriteRespDTO favoriteResult = articleService.favoriteArticle(favoriteReqDTO);
            assertTrue(favoriteResult.getFavorited());
        }
    }

    @Test
    void favoriteArticle_fullCycle_favoriteCancelRefavorite() {
        when(articleMapper.selectById(ARTICLE_ID)).thenReturn(articleDO);
        when(articleMapper.selectOne(any())).thenReturn(articleDO);
        when(userFavoriteArticleMapper.softDeleteById(anyLong())).thenReturn(1);
        when(userFavoriteArticleMapper.softRestoreById(anyLong())).thenReturn(1);
        when(articleMapper.decrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);

        UserFavoriteArticleDO active1 = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 0);
        UserFavoriteArticleDO inactive = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 1);
        UserFavoriteArticleDO active2 = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 0);
        UserFavoriteArticleDO inactive2 = createFavoriteDO(1L, ARTICLE_ID, USER_ID, 1);

        when(userFavoriteArticleMapper.selectByUserAndArticle(anyLong(), anyLong()))
                .thenReturn(active1)
                .thenReturn(inactive)
                .thenReturn(active2)
                .thenReturn(inactive2);

        ArticleFavoriteRespDTO cancelResult = articleService.favoriteArticle(favoriteReqDTO);
        assertFalse(cancelResult.getFavorited());

        ArticleFavoriteRespDTO refavoriteResult = articleService.favoriteArticle(favoriteReqDTO);
        assertTrue(refavoriteResult.getFavorited());

        ArticleFavoriteRespDTO cancelResult2 = articleService.favoriteArticle(favoriteReqDTO);
        assertFalse(cancelResult2.getFavorited());

        ArticleFavoriteRespDTO refavoriteResult2 = articleService.favoriteArticle(favoriteReqDTO);
        assertTrue(refavoriteResult2.getFavorited());
    }
}
