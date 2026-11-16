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
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.CategoryService;
import io.github.somehow.mysite.service.CollectionService;
import io.github.somehow.mysite.service.TagService;
import io.github.somehow.mysite.support.TestTableInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * selectOneArticle 草稿门单元测试。
 *
 * 详情走 Redis 缓存（跨用户共享），草稿/私有的拦截必须在缓存之后、按请求者实时校验：
 *   - published != 1：仅作者本人或 ADMIN 可见，其他一律按不存在处理（404 语义）
 *   - published == 1：正常放行
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleServiceImpl.selectOneArticle 草稿门")
class ArticleServiceImplDraftTest {

    @BeforeAll
    static void initTableInfo() {
        TestTableInfo.initAll();
    }

    private static final Long ARTICLE_ID = 1L;
    private static final Long AUTHOR_ID = 100L;
    private static final Long OTHER_USER_ID = 999L;

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

    @BeforeEach
    void setUp() {
        // 默认：普通登录用户（非作者、非管理员）
        UserContext.setUser(UserInfoDTO.builder()
                .userId(OTHER_USER_ID.toString())
                .role(UserRole.USER)
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    private ArticleSelectRespDTO cachedArticle(Integer published, Integer visibility) {
        ArticleSelectRespDTO dto = new ArticleSelectRespDTO();
        dto.setId(ARTICLE_ID);
        dto.setAuthorId(AUTHOR_ID);
        dto.setPublished(published);
        dto.setVisibility(visibility);
        dto.setViewCount(0);
        return dto;
    }

    private void stubCache(ArticleSelectRespDTO dto) {
        when(articleCacheService.getArticleDetail(ARTICLE_ID)).thenReturn(dto);
        // getPendingViewCount 未 stub 时 Mockito 默认返回 0L，无需显式打桩
    }

    @Test
    @DisplayName("匿名访问草稿 → 404（与不存在同语义）")
    void draft_guest_shouldNotFound() {
        UserContext.removeUser();
        stubCache(cachedArticle(0, 0));

        ClientException ex = assertThrows(ClientException.class,
                () -> articleService.selectOneArticle(ARTICLE_ID));
        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.code(), ex.errorCode);
    }

    @Test
    @DisplayName("其他登录用户访问草稿 → 404")
    void draft_otherUser_shouldNotFound() {
        stubCache(cachedArticle(0, 0));

        assertThrows(ClientException.class, () -> articleService.selectOneArticle(ARTICLE_ID));
    }

    @Test
    @DisplayName("作者本人访问自己的草稿 → 正常返回")
    void draft_author_shouldPass() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId(AUTHOR_ID.toString())
                .role(UserRole.CREATOR)
                .build());
        stubCache(cachedArticle(0, 0));

        ArticleSelectRespDTO result = articleService.selectOneArticle(ARTICLE_ID);
        assertEquals(ARTICLE_ID, result.getId());
    }

    @Test
    @DisplayName("管理员访问草稿 → 正常返回")
    void draft_admin_shouldPass() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1")
                .role(UserRole.ADMIN)
                .build());
        stubCache(cachedArticle(0, 1)); // 顺带覆盖：私有+草稿对管理员也放行

        ArticleSelectRespDTO result = articleService.selectOneArticle(ARTICLE_ID);
        assertEquals(ARTICLE_ID, result.getId());
    }

    @Test
    @DisplayName("已发布文章 → 其他用户正常访问")
    void published_otherUser_shouldPass() {
        stubCache(cachedArticle(1, 0));

        ArticleSelectRespDTO result = articleService.selectOneArticle(ARTICLE_ID);
        assertEquals(ARTICLE_ID, result.getId());
        assertFalse(result.getIsFavorited());
    }
}
