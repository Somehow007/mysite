package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.context.UserInfoDTO;
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
import io.github.somehow.mysite.dto.resp.collection.ArticleNavInfoRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionDetailRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合集可见性单元测试：私有文章（visibility=1，仅自己可见）不应泄露给游客/其他用户。
 * 纯 Mockito 单测（无 Spring 代理），@Cacheable 不生效，直接验证 Service 逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("合集私有文章可见性测试")
class CollectionServiceVisibilityTest {

    private static final long COLLECTION_ID = 1L;
    private static final long AUTHOR_ID = 100L;
    private static final long PUBLIC_ARTICLE_ID = 10L;
    private static final long PRIVATE_ARTICLE_ID = 11L;

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

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    private void loginAs(String userId, UserRole role) {
        UserContext.setUser(UserInfoDTO.builder().userId(userId).role(role).build());
    }

    private CollectionDO collection() {
        return CollectionDO.builder()
                .id(COLLECTION_ID)
                .title("测试合集")
                .authorId(AUTHOR_ID)
                .articleCount(2)
                .build();
    }

    private List<CollectionArticleDO> relations() {
        return List.of(
                CollectionArticleDO.builder().id(1L).collectionId(COLLECTION_ID)
                        .articleId(PUBLIC_ARTICLE_ID).sortOrder(0).build(),
                CollectionArticleDO.builder().id(2L).collectionId(COLLECTION_ID)
                        .articleId(PRIVATE_ARTICLE_ID).sortOrder(1).build());
    }

    private ArticleDO publicArticle() {
        return ArticleDO.builder().id(PUBLIC_ARTICLE_ID).title("公开文章")
                .authorId(AUTHOR_ID).published(1).visibility(0).build();
    }

    private ArticleDO privateArticle() {
        return ArticleDO.builder().id(PRIVATE_ARTICLE_ID).title("私有文章")
                .authorId(AUTHOR_ID).published(1).visibility(1).build();
    }

    private UserDO author() {
        UserDO user = new UserDO();
        user.setId(AUTHOR_ID);
        user.setUsername("作者");
        return user;
    }

    // ==================== 合集详情 ====================

    @Test
    @DisplayName("游客查看合集详情 - 私有文章应被过滤")
    void guestShouldNotSeePrivateArticles() {
        when(collectionMapper.selectOne(any())).thenReturn(collection());
        when(userMapper.selectById(anyLong())).thenReturn(author());
        when(collectionArticleMapper.selectList(any())).thenReturn(relations());
        // 第 1 次 selectList：可见性检查；第 2 次：分页拉取文章
        when(articleMapper.selectList(any()))
                .thenReturn(List.of(publicArticle(), privateArticle()))
                .thenReturn(List.of(publicArticle()));
        when(userMapper.selectList(any())).thenReturn(List.of(author()));

        CollectionDetailRespDTO detail = collectionService.getCollectionDetail(COLLECTION_ID, 1, 10);

        assertEquals(1, detail.getArticles().size());
        assertEquals(PUBLIC_ARTICLE_ID, detail.getArticles().get(0).getId());
        // 计数按可见数量修正
        assertEquals(1, detail.getArticleCount());
    }

    @Test
    @DisplayName("其他登录用户查看合集详情 - 私有文章应被过滤")
    void otherUserShouldNotSeePrivateArticles() {
        loginAs("999", UserRole.USER);

        when(collectionMapper.selectOne(any())).thenReturn(collection());
        when(userMapper.selectById(anyLong())).thenReturn(author());
        when(collectionArticleMapper.selectList(any())).thenReturn(relations());
        when(articleMapper.selectList(any()))
                .thenReturn(List.of(publicArticle(), privateArticle()))
                .thenReturn(List.of(publicArticle()));
        when(userMapper.selectList(any())).thenReturn(List.of(author()));

        CollectionDetailRespDTO detail = collectionService.getCollectionDetail(COLLECTION_ID, 1, 10);

        assertEquals(1, detail.getArticles().size());
        assertEquals(PUBLIC_ARTICLE_ID, detail.getArticles().get(0).getId());
        assertEquals(1, detail.getArticleCount());
    }

    @Test
    @DisplayName("作者本人查看合集详情 - 可见自己的私有文章")
    void authorShouldSeeOwnPrivateArticles() {
        loginAs(String.valueOf(AUTHOR_ID), UserRole.CREATOR);

        when(collectionMapper.selectOne(any())).thenReturn(collection());
        when(userMapper.selectById(anyLong())).thenReturn(author());
        when(collectionArticleMapper.selectList(any())).thenReturn(relations());
        when(articleMapper.selectList(any()))
                .thenReturn(List.of(publicArticle(), privateArticle()))
                .thenReturn(List.of(publicArticle(), privateArticle()));
        when(userMapper.selectList(any())).thenReturn(List.of(author()));

        CollectionDetailRespDTO detail = collectionService.getCollectionDetail(COLLECTION_ID, 1, 10);

        assertEquals(2, detail.getArticles().size());
        assertEquals(2, detail.getArticleCount());
    }

    @Test
    @DisplayName("管理员查看合集详情 - 可见所有私有文章且跳过可见性检查查询")
    void adminShouldSeeAllPrivateArticles() {
        loginAs("888", UserRole.ADMIN);

        when(collectionMapper.selectOne(any())).thenReturn(collection());
        when(userMapper.selectById(anyLong())).thenReturn(author());
        when(collectionArticleMapper.selectList(any())).thenReturn(relations());
        when(articleMapper.selectList(any()))
                .thenReturn(List.of(publicArticle(), privateArticle()));
        when(userMapper.selectList(any())).thenReturn(List.of(author()));

        CollectionDetailRespDTO detail = collectionService.getCollectionDetail(COLLECTION_ID, 1, 10);

        assertEquals(2, detail.getArticles().size());
        // 管理员直接跳过可见性检查，articleMapper.selectList 只调用一次（分页拉取）
        verify(articleMapper, times(1)).selectList(any());
    }

    // ==================== 合集文章导航 ====================

    @Test
    @DisplayName("游客获取文章导航 - 下一篇为私有文章时不返回")
    void guestNavShouldSkipPrivateNextArticle() {
        when(collectionArticleMapper.selectOne(any())).thenReturn(relations().get(0));
        when(collectionMapper.selectOne(any())).thenReturn(collection());
        when(collectionArticleMapper.selectList(any())).thenReturn(relations());
        // 私有文章对游客不可见 → selectOne 返回 null
        when(articleMapper.selectOne(any())).thenReturn(null);

        ArticleNavInfoRespDTO nav = collectionService.getArticleNavigation(PUBLIC_ARTICLE_ID);

        assertTrue(nav.getInCollection());
        assertNull(nav.getNext());
        assertNull(nav.getPrev());
    }

    // ==================== 合集手动可见性 ====================

    private CollectionDO privateCollection() {
        CollectionDO c = collection();
        c.setVisibility(1);
        return c;
    }

    @Test
    @DisplayName("游客访问私有合集详情 - 应抛出 COLLECTION_NOT_FOUND")
    void guestShouldNotAccessPrivateCollectionDetail() {
        when(collectionMapper.selectOne(any())).thenReturn(privateCollection());

        ClientException ex = assertThrows(ClientException.class,
                () -> collectionService.getCollectionDetail(COLLECTION_ID, 1, 10));
        assertEquals(ErrorCode.COLLECTION_NOT_FOUND.code(), ex.getErrorCode());
    }

    @Test
    @DisplayName("其他用户访问私有合集详情 - 应抛出 COLLECTION_NOT_FOUND")
    void otherUserShouldNotAccessPrivateCollectionDetail() {
        loginAs("999", UserRole.USER);
        when(collectionMapper.selectOne(any())).thenReturn(privateCollection());

        ClientException ex = assertThrows(ClientException.class,
                () -> collectionService.getCollectionDetail(COLLECTION_ID, 1, 10));
        assertEquals(ErrorCode.COLLECTION_NOT_FOUND.code(), ex.getErrorCode());
    }

    @Test
    @DisplayName("作者访问自己的私有合集详情 - 正常返回")
    void ownerShouldAccessPrivateCollectionDetail() {
        loginAs(String.valueOf(AUTHOR_ID), UserRole.CREATOR);

        when(collectionMapper.selectOne(any())).thenReturn(privateCollection());
        when(userMapper.selectById(anyLong())).thenReturn(author());
        when(collectionArticleMapper.selectList(any())).thenReturn(relations());
        when(articleMapper.selectList(any()))
                .thenReturn(List.of(publicArticle(), privateArticle()))
                .thenReturn(List.of(publicArticle(), privateArticle()));
        when(userMapper.selectList(any())).thenReturn(List.of(author()));

        CollectionDetailRespDTO detail = collectionService.getCollectionDetail(COLLECTION_ID, 1, 10);

        assertEquals(2, detail.getArticles().size());
        assertEquals(1, detail.getVisibility());
    }

    @Test
    @DisplayName("isCollectionVisibleToCurrentUser - 各角色判定正确")
    void collectionVisibilityCheckPerRole() {
        // 合集不存在
        when(collectionMapper.selectOne(any())).thenReturn(null);
        assertFalse(collectionService.isCollectionVisibleToCurrentUser(COLLECTION_ID));

        // 公开合集 → 游客可见
        when(collectionMapper.selectOne(any())).thenReturn(collection());
        assertTrue(collectionService.isCollectionVisibleToCurrentUser(COLLECTION_ID));

        // 私有合集 → 游客不可见
        when(collectionMapper.selectOne(any())).thenReturn(privateCollection());
        assertFalse(collectionService.isCollectionVisibleToCurrentUser(COLLECTION_ID));

        // 私有合集 → 作者可见
        loginAs(String.valueOf(AUTHOR_ID), UserRole.CREATOR);
        assertTrue(collectionService.isCollectionVisibleToCurrentUser(COLLECTION_ID));

        // 私有合集 → 管理员可见
        loginAs("888", UserRole.ADMIN);
        assertTrue(collectionService.isCollectionVisibleToCurrentUser(COLLECTION_ID));
    }

    @Test
    @DisplayName("游客获取文章导航 - 文章属于私有合集时回退为时间线导航")
    void guestNavShouldFallbackWhenCollectionPrivate() {
        when(collectionArticleMapper.selectOne(any())).thenReturn(relations().get(0));
        when(collectionMapper.selectOne(any())).thenReturn(privateCollection());
        // 回退时间线导航：上一篇/下一篇查询（published=1 + 可见性过滤）
        when(articleMapper.selectOne(any())).thenReturn(null);

        ArticleNavInfoRespDTO nav = collectionService.getArticleNavigation(PUBLIC_ARTICLE_ID);

        assertFalse(nav.getInCollection());
        assertNull(nav.getCollectionId());
        assertNull(nav.getCollectionTitle());
    }

    @Test
    @DisplayName("作者获取文章导航 - 可见自己的私有下一篇")
    void authorNavShouldIncludeOwnPrivateNextArticle() {
        loginAs(String.valueOf(AUTHOR_ID), UserRole.CREATOR);

        when(collectionArticleMapper.selectOne(any())).thenReturn(relations().get(0));
        when(collectionMapper.selectOne(any())).thenReturn(collection());
        when(collectionArticleMapper.selectList(any())).thenReturn(relations());
        when(articleMapper.selectOne(any())).thenReturn(privateArticle());

        ArticleNavInfoRespDTO nav = collectionService.getArticleNavigation(PUBLIC_ARTICLE_ID);

        assertTrue(nav.getInCollection());
        assertNotNull(nav.getNext());
        assertEquals(String.valueOf(PRIVATE_ARTICLE_ID), nav.getNext().getId());
    }
}
