package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.context.UserInfoDTO;
import io.github.somehow.mysite.commons.enums.UserRole;
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
import io.github.somehow.mysite.dto.req.collection.CollectionUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.collection.ArticleNavInfoRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionDetailRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private CollectionArticleMapper collectionArticleMapper;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private CollectionServiceImpl collectionService;

    private static final Long COLLECTION_ID = 100001L;
    private static final Long AUTHOR_ID = 987654321L;
    private static final Long ARTICLE_ID_1 = 111111L;
    private static final Long ARTICLE_ID_2 = 222222L;
    private static final Long ARTICLE_ID_3 = 333333L;

    @BeforeEach
    void setUp() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId(AUTHOR_ID.toString())
                .role(UserRole.USER)
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    private CollectionDO createCollectionDO(Long id, Long authorId) {
        CollectionDO collection = new CollectionDO();
        collection.setId(id);
        collection.setAuthorId(authorId);
        collection.setTitle("测试合集");
        collection.setArticleCount(0);
        collection.setDelFlag(0);
        return collection;
    }

    private ArticleDO createArticleDO(Long id, Integer published) {
        ArticleDO article = new ArticleDO();
        article.setId(id);
        article.setTitle("测试文章" + id);
        article.setAuthorId(AUTHOR_ID);
        article.setPublished(published);
        article.setDelFlag(0);
        return article;
    }

    private CollectionArticleDO createCollectionArticleDO(Long id, Long collectionId, Long articleId, Integer sortOrder) {
        return CollectionArticleDO.builder()
                .id(id)
                .collectionId(collectionId)
                .articleId(articleId)
                .sortOrder(sortOrder)
                .build();
    }

    // ==================== 创建合集测试 ====================

    @Test
    void createCollection_normal_shouldSucceed() {
        CollectionCreateReqDTO req = new CollectionCreateReqDTO();
        req.setTitle("我的Spring Boot教程");
        req.setDescription("从入门到精通");

        when(collectionMapper.insert(any(CollectionDO.class))).thenReturn(1);

        Long id = collectionService.createCollection(req);

        assertNotNull(id);
        verify(collectionMapper).insert(any(CollectionDO.class));
    }

    @Test
    void createCollection_titleEmpty_shouldThrowException() {
        CollectionCreateReqDTO req = new CollectionCreateReqDTO();
        req.setTitle("");

        assertThrows(ClientException.class, () -> collectionService.createCollection(req));
        verify(collectionMapper, never()).insert(any(CollectionDO.class));
    }

    @Test
    void createCollection_noAuth_shouldThrowException() {
        UserContext.removeUser();

        CollectionCreateReqDTO req = new CollectionCreateReqDTO();
        req.setTitle("测试合集");

        assertThrows(ClientException.class, () -> collectionService.createCollection(req));
    }

    // ==================== 更新合集测试 ====================

    @Test
    void updateCollection_normal_shouldSucceed() {
        CollectionDO existing = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(existing);

        CollectionUpdateReqDTO req = new CollectionUpdateReqDTO();
        req.setTitle("更新后的标题");

        assertDoesNotThrow(() -> collectionService.updateCollection(COLLECTION_ID, req));
        verify(collectionMapper).updateById(any(CollectionDO.class));
    }

    @Test
    void updateCollection_notFound_shouldThrowException() {
        when(collectionMapper.selectOne(any())).thenReturn(null);

        CollectionUpdateReqDTO req = new CollectionUpdateReqDTO();
        req.setTitle("更新后的标题");

        assertThrows(ClientException.class, () -> collectionService.updateCollection(COLLECTION_ID, req));
    }

    @Test
    void updateCollection_notOwner_shouldThrowException() {
        CollectionDO existing = createCollectionDO(COLLECTION_ID, 999999L);
        when(collectionMapper.selectOne(any())).thenReturn(existing);

        CollectionUpdateReqDTO req = new CollectionUpdateReqDTO();
        req.setTitle("更新后的标题");

        assertThrows(ClientException.class, () -> collectionService.updateCollection(COLLECTION_ID, req));
    }

    // ==================== 删除合集测试 ====================

    @Test
    void deleteCollection_normal_shouldSucceed() {
        CollectionDO existing = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(existing);

        assertDoesNotThrow(() -> collectionService.deleteCollection(COLLECTION_ID));
        verify(collectionMapper).updateById(any(CollectionDO.class));
        verify(collectionArticleMapper).physicalDeleteByCollectionId(COLLECTION_ID);
    }

    // ==================== 添加文章到合集测试 ====================

    @Test
    void addArticleToCollection_normal_shouldSucceed() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        ArticleDO article = createArticleDO(ARTICLE_ID_1, 1);
        when(articleMapper.selectOne(any())).thenReturn(article);

        when(collectionArticleMapper.selectOne(any())).thenReturn(null);
        when(collectionArticleMapper.selectMaxSortOrder(COLLECTION_ID)).thenReturn(-1);

        assertDoesNotThrow(() -> collectionService.addArticleToCollection(COLLECTION_ID, ARTICLE_ID_1));
        verify(collectionArticleMapper).insert(any(CollectionArticleDO.class));
        verify(collectionMapper).updateArticleCount(COLLECTION_ID, 1);
    }

    @Test
    void addArticleToCollection_alreadyExists_shouldThrowException() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        ArticleDO article = createArticleDO(ARTICLE_ID_1, 1);
        when(articleMapper.selectOne(any())).thenReturn(article);

        CollectionArticleDO existing = createCollectionArticleDO(1L, COLLECTION_ID, ARTICLE_ID_1, 0);
        when(collectionArticleMapper.selectOne(any())).thenReturn(existing);

        assertThrows(ClientException.class, () -> collectionService.addArticleToCollection(COLLECTION_ID, ARTICLE_ID_1));
        verify(collectionArticleMapper, never()).insert(any(CollectionArticleDO.class));
    }

    @Test
    void addArticleToCollection_articleNotFound_shouldThrowException() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        when(articleMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> collectionService.addArticleToCollection(COLLECTION_ID, ARTICLE_ID_1));
    }

    // ==================== 移除文章测试 ====================

    @Test
    void removeArticleFromCollection_normal_shouldSucceed() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        CollectionArticleDO existing = createCollectionArticleDO(1L, COLLECTION_ID, ARTICLE_ID_1, 0);
        when(collectionArticleMapper.selectOne(any())).thenReturn(existing);

        assertDoesNotThrow(() -> collectionService.removeArticleFromCollection(COLLECTION_ID, ARTICLE_ID_1));
        verify(collectionArticleMapper).deleteById(1L);
        verify(collectionMapper).updateArticleCount(COLLECTION_ID, -1);
    }

    @Test
    void removeArticleFromCollection_notInCollection_shouldThrowException() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        when(collectionArticleMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> collectionService.removeArticleFromCollection(COLLECTION_ID, ARTICLE_ID_1));
    }

    // ==================== 批量添加文章测试 ====================

    @Test
    void batchAddArticles_normal_shouldSucceed() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        when(collectionArticleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(collectionArticleMapper.selectMaxSortOrder(COLLECTION_ID)).thenReturn(-1);

        CollectionArticleBatchReqDTO req = new CollectionArticleBatchReqDTO();
        req.setArticleIds(Arrays.asList(ARTICLE_ID_1, ARTICLE_ID_2));

        collectionService.batchAddArticles(COLLECTION_ID, req);

        verify(collectionArticleMapper).batchInsert(any());
        verify(collectionMapper).updateArticleCount(COLLECTION_ID, 2);
    }

    @Test
    void batchAddArticles_skipExisting_shouldOnlyInsertNew() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        CollectionArticleDO existing = createCollectionArticleDO(1L, COLLECTION_ID, ARTICLE_ID_1, 0);
        when(collectionArticleMapper.selectList(any())).thenReturn(List.of(existing));
        when(collectionArticleMapper.selectMaxSortOrder(COLLECTION_ID)).thenReturn(0);

        CollectionArticleBatchReqDTO req = new CollectionArticleBatchReqDTO();
        req.setArticleIds(Arrays.asList(ARTICLE_ID_1, ARTICLE_ID_2));

        collectionService.batchAddArticles(COLLECTION_ID, req);

        verify(collectionArticleMapper).batchInsert(any());
        verify(collectionMapper).updateArticleCount(COLLECTION_ID, 1);
    }

    // ==================== 排序更新测试 ====================

    @Test
    void updateArticleSort_normal_shouldSucceed() {
        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        CollectionArticleDO ca1 = createCollectionArticleDO(10L, COLLECTION_ID, ARTICLE_ID_1, 0);
        CollectionArticleDO ca2 = createCollectionArticleDO(20L, COLLECTION_ID, ARTICLE_ID_2, 1);
        when(collectionArticleMapper.selectList(any())).thenReturn(Arrays.asList(ca1, ca2));

        CollectionArticleSortReqDTO req = new CollectionArticleSortReqDTO();
        req.setArticleIds(Arrays.asList(ARTICLE_ID_2, ARTICLE_ID_1));

        collectionService.updateArticleSort(COLLECTION_ID, req);

        verify(collectionArticleMapper).updateSortOrder(20L, 0);
        verify(collectionArticleMapper).updateSortOrder(10L, 1);
    }

    // ==================== 文章导航测试 ====================

    @Test
    void getArticleNavigation_inCollection_middleArticle_shouldReturnPrevAndNext() {
        CollectionArticleDO ca = createCollectionArticleDO(1L, COLLECTION_ID, ARTICLE_ID_2, 1);
        when(collectionArticleMapper.selectOne(any())).thenReturn(ca);

        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        List<CollectionArticleDO> allArticles = Arrays.asList(
                createCollectionArticleDO(10L, COLLECTION_ID, ARTICLE_ID_1, 0),
                createCollectionArticleDO(20L, COLLECTION_ID, ARTICLE_ID_2, 1),
                createCollectionArticleDO(30L, COLLECTION_ID, ARTICLE_ID_3, 2)
        );
        when(collectionArticleMapper.selectList(any())).thenReturn(allArticles);

        ArticleDO prevArticle = createArticleDO(ARTICLE_ID_1, 1);
        ArticleDO nextArticle = createArticleDO(ARTICLE_ID_3, 1);
        when(articleMapper.selectOne(any())).thenReturn(prevArticle, nextArticle);

        ArticleNavInfoRespDTO navInfo = collectionService.getArticleNavigation(ARTICLE_ID_2);

        assertTrue(navInfo.getInCollection());
        assertEquals(COLLECTION_ID.toString(), navInfo.getCollectionId());
        assertNotNull(navInfo.getPrev());
        assertEquals(ARTICLE_ID_1.toString(), navInfo.getPrev().getId());
        assertNotNull(navInfo.getNext());
        assertEquals(ARTICLE_ID_3.toString(), navInfo.getNext().getId());
    }

    @Test
    void getArticleNavigation_inCollection_firstArticle_prevShouldBeNull() {
        CollectionArticleDO ca = createCollectionArticleDO(1L, COLLECTION_ID, ARTICLE_ID_1, 0);
        when(collectionArticleMapper.selectOne(any())).thenReturn(ca);

        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        List<CollectionArticleDO> allArticles = Arrays.asList(
                createCollectionArticleDO(10L, COLLECTION_ID, ARTICLE_ID_1, 0),
                createCollectionArticleDO(20L, COLLECTION_ID, ARTICLE_ID_2, 1)
        );
        when(collectionArticleMapper.selectList(any())).thenReturn(allArticles);

        ArticleDO nextArticle = createArticleDO(ARTICLE_ID_2, 1);
        when(articleMapper.selectOne(any())).thenReturn(nextArticle);

        ArticleNavInfoRespDTO navInfo = collectionService.getArticleNavigation(ARTICLE_ID_1);

        assertTrue(navInfo.getInCollection());
        assertNull(navInfo.getPrev());
        assertNotNull(navInfo.getNext());
        assertEquals(ARTICLE_ID_2.toString(), navInfo.getNext().getId());
    }

    @Test
    void getArticleNavigation_inCollection_lastArticle_nextShouldBeNull() {
        CollectionArticleDO ca = createCollectionArticleDO(1L, COLLECTION_ID, ARTICLE_ID_2, 1);
        when(collectionArticleMapper.selectOne(any())).thenReturn(ca);

        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        List<CollectionArticleDO> allArticles = Arrays.asList(
                createCollectionArticleDO(10L, COLLECTION_ID, ARTICLE_ID_1, 0),
                createCollectionArticleDO(20L, COLLECTION_ID, ARTICLE_ID_2, 1)
        );
        when(collectionArticleMapper.selectList(any())).thenReturn(allArticles);

        ArticleDO prevArticle = createArticleDO(ARTICLE_ID_1, 1);
        when(articleMapper.selectOne(any())).thenReturn(prevArticle);

        ArticleNavInfoRespDTO navInfo = collectionService.getArticleNavigation(ARTICLE_ID_2);

        assertTrue(navInfo.getInCollection());
        assertNotNull(navInfo.getPrev());
        assertEquals(ARTICLE_ID_1.toString(), navInfo.getPrev().getId());
        assertNull(navInfo.getNext());
    }

    @Test
    void getArticleNavigation_notInCollection_shouldReturnTimeBasedNav() {
        when(collectionArticleMapper.selectOne(any())).thenReturn(null);

        ArticleDO currentArticle = createArticleDO(ARTICLE_ID_2, 1);
        ArticleDO prevArticle = createArticleDO(ARTICLE_ID_1, 1);
        ArticleDO nextArticle = createArticleDO(ARTICLE_ID_3, 1);
        when(articleMapper.selectOne(any())).thenReturn(currentArticle, prevArticle, nextArticle);

        ArticleNavInfoRespDTO navInfo = collectionService.getArticleNavigation(ARTICLE_ID_2);

        assertFalse(navInfo.getInCollection());
        assertNull(navInfo.getCollectionId());
        assertNotNull(navInfo.getPrev());
        assertNotNull(navInfo.getNext());
    }

    @Test
    void getArticleNavigation_inCollection_shouldFilterDraftArticles() {
        CollectionArticleDO ca = createCollectionArticleDO(1L, COLLECTION_ID, ARTICLE_ID_2, 1);
        when(collectionArticleMapper.selectOne(any())).thenReturn(ca);

        CollectionDO collection = createCollectionDO(COLLECTION_ID, AUTHOR_ID);
        when(collectionMapper.selectOne(any())).thenReturn(collection);

        List<CollectionArticleDO> allArticles = Arrays.asList(
                createCollectionArticleDO(10L, COLLECTION_ID, ARTICLE_ID_1, 0),
                createCollectionArticleDO(20L, COLLECTION_ID, ARTICLE_ID_2, 1),
                createCollectionArticleDO(30L, COLLECTION_ID, ARTICLE_ID_3, 2)
        );
        when(collectionArticleMapper.selectList(any())).thenReturn(allArticles);

        // 上一篇文章是草稿（published=0），应该返回null
        ArticleDO draftArticle = createArticleDO(ARTICLE_ID_1, 0);
        when(articleMapper.selectOne(any())).thenReturn(draftArticle);

        ArticleNavInfoRespDTO navInfo = collectionService.getArticleNavigation(ARTICLE_ID_2);

        assertTrue(navInfo.getInCollection());
        assertNull(navInfo.getPrev());
    }
}
