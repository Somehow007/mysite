package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.ragent.core.ReadingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ArticleReadingContextService — 文章页阅读上下文")
class ArticleReadingContextServiceTest {

    private ArticleMapper articleMapper;
    private ArticleReadingContextService service;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        service = new ArticleReadingContextService(articleMapper);
    }

    private ArticleDO article(Long id, String title, String content, Integer published, Integer visibility) {
        ArticleDO a = new ArticleDO();
        a.setId(id);
        a.setTitle(title);
        a.setContent(content);
        a.setPublished(published);
        a.setVisibility(visibility);
        a.setAuthorId(99L);
        return a;
    }

    @Test
    @DisplayName("articleId 为 null 时返回 null（非文章页零影响）")
    void nullArticleIdReturnsNull() {
        assertNull(service.load(null));
    }

    @Test
    @DisplayName("公开已发布文章应加载标题与正文")
    void loadsPublicPublishedArticle() {
        when(articleMapper.selectById(1L))
            .thenReturn(article(1L, "JWT 实践", "正文内容", 1, 0));

        ReadingContext ctx = service.load(1L);
        assertNotNull(ctx);
        assertEquals(1L, ctx.articleId());
        assertEquals("JWT 实践", ctx.title());
        assertEquals("正文内容", ctx.content());
    }

    @Test
    @DisplayName("未发布或私有文章对匿名访客不可见")
    void hidesUnpublishedAndPrivate() {
        when(articleMapper.selectById(2L))
            .thenReturn(article(2L, "草稿", "x", 0, 0));
        when(articleMapper.selectById(3L))
            .thenReturn(article(3L, "私有", "x", 1, 1));

        assertNull(service.load(2L));
        assertNull(service.load(3L));
    }

    @Test
    @DisplayName("超长正文截断到 MAX_CONTENT_CHARS")
    void truncatesLongContent() {
        String longContent = "a".repeat(ReadingContext.MAX_CONTENT_CHARS + 100);
        ReadingContext ctx = ReadingContext.of(9L, "长文", longContent);
        assertTrue(ctx.content().length() <= ReadingContext.MAX_CONTENT_CHARS + 20);
        assertTrue(ctx.content().contains("已截断"));
    }
}
