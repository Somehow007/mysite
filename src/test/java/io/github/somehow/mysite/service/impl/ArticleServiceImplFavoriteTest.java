package io.github.somehow.mysite.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dto.req.article.ArticleFavoriteReqDTO;
import io.github.somehow.mysite.dto.resp.ArticleFavoriteRespDTO;
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

    @InjectMocks
    private ArticleServiceImpl articleService;

    private ArticleFavoriteReqDTO favoriteReqDTO;
    private ArticleDO articleDO;

    @BeforeEach
    void setUp() {
        favoriteReqDTO = new ArticleFavoriteReqDTO();
        favoriteReqDTO.setArticleId("123456789");
        favoriteReqDTO.setUserId("987654321");

        articleDO = new ArticleDO();
        articleDO.setId(123456789L);
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
        when(userFavoriteArticleMapper.selectOne(any())).thenReturn(null);
        when(userFavoriteArticleMapper.insert(any(UserFavoriteArticleDO.class))).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(123456789L)).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper).selectOne(any());
        verify(userFavoriteArticleMapper).insert(any(UserFavoriteArticleDO.class));
        verify(articleMapper).incrementFavoriteCount(123456789L, 1);

        assertTrue(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_existingFavorite_shouldCancel() {
        UserFavoriteArticleDO existing = createFavoriteDO(1L, 123456789L, 987654321L, 0);
        when(userFavoriteArticleMapper.selectOne(any())).thenReturn(existing);
        when(userFavoriteArticleMapper.update(any(), any())).thenReturn(1);
        when(articleMapper.decrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(123456789L)).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper).selectOne(any());
        verify(userFavoriteArticleMapper).update(any(), any());
        verify(articleMapper).decrementFavoriteCount(123456789L, 1);

        assertFalse(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_canceledFavorite_shouldRestore() {
        UserFavoriteArticleDO existing = createFavoriteDO(1L, 123456789L, 987654321L, 1);
        when(userFavoriteArticleMapper.selectOne(any())).thenReturn(existing);
        when(userFavoriteArticleMapper.update(any(), any())).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(123456789L)).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper).selectOne(any());
        verify(userFavoriteArticleMapper).update(any(), any());
        verify(articleMapper).incrementFavoriteCount(123456789L, 1);

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
        when(articleMapper.selectById(123456789L)).thenReturn(null);

        assertThrows(ClientException.class, () -> articleService.favoriteArticle(favoriteReqDTO));

        verify(userFavoriteArticleMapper, never()).selectOne(any());
    }

    @Test
    void favoriteArticle_deletedArticle_shouldThrowException() {
        ArticleDO deletedArticle = new ArticleDO();
        deletedArticle.setId(123456789L);
        deletedArticle.setDelFlag(1);
        when(articleMapper.selectById(123456789L)).thenReturn(deletedArticle);

        assertThrows(ClientException.class, () -> articleService.favoriteArticle(favoriteReqDTO));

        verify(userFavoriteArticleMapper, never()).selectOne(any());
    }

    @Test
    void favoriteArticle_duplicateKey_restoreDeletedRecord() {
        when(userFavoriteArticleMapper.selectOne(any())).thenReturn(null);
        when(userFavoriteArticleMapper.insert(any(UserFavoriteArticleDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        UserFavoriteArticleDO duplicate = createFavoriteDO(1L, 123456789L, 987654321L, 1);
        when(userFavoriteArticleMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(duplicate);
        when(userFavoriteArticleMapper.update(any(), any())).thenReturn(1);
        when(articleMapper.incrementFavoriteCount(anyLong(), anyInt())).thenReturn(1);
        when(articleMapper.selectById(123456789L)).thenReturn(articleDO);

        ArticleFavoriteRespDTO result = articleService.favoriteArticle(favoriteReqDTO);

        verify(userFavoriteArticleMapper, times(2)).selectOne(any());
        verify(userFavoriteArticleMapper).insert(any(UserFavoriteArticleDO.class));
        verify(userFavoriteArticleMapper).update(any(), any());
        verify(articleMapper).incrementFavoriteCount(123456789L, 1);

        assertTrue(result.getFavorited());
        assertEquals(5, result.getFavoriteCount());
    }

    @Test
    void favoriteArticle_duplicateKey_throwWhenAlreadyActive() {
        when(articleMapper.selectById(123456789L)).thenReturn(articleDO);
        when(userFavoriteArticleMapper.insert(any(UserFavoriteArticleDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        UserFavoriteArticleDO duplicate = createFavoriteDO(1L, 123456789L, 987654321L, 0);
        when(userFavoriteArticleMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(duplicate);

        assertThrows(ClientException.class, () -> articleService.favoriteArticle(favoriteReqDTO));
    }
}