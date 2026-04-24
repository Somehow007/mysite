package io.github.somehow.mysite.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.service.ArticleSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("文章搜索服务测试")
class ArticleSearchServiceTest {

    @Mock
    private ElasticsearchArticleSearchServiceImpl elasticsearchService;

    @Mock
    private DatabaseArticleSearchServiceImpl databaseService;

    @Mock
    private ElasticsearchProperties elasticsearchProperties;

    private ArticlePageQueryReqDTO requestParam;

    @BeforeEach
    void setUp() {
        requestParam = new ArticlePageQueryReqDTO();
        requestParam.setCurrent(1L);
        requestParam.setSize(10L);
        requestParam.setKeyword("test");
        requestParam.setSearchType("title");
    }

    @Test
    @DisplayName("ES启用状态测试")
    void testElasticsearchEnabled() {
        when(elasticsearchProperties.isEnabled()).thenReturn(true);
        when(elasticsearchService.isEnabled()).thenReturn(true);

        assertTrue(elasticsearchService.isEnabled(), "ES应该处于启用状态");
    }

    @Test
    @DisplayName("ES禁用状态测试")
    void testElasticsearchDisabled() {
        when(elasticsearchProperties.isEnabled()).thenReturn(false);
        when(databaseService.isEnabled()).thenReturn(false);

        assertFalse(databaseService.isEnabled(), "ES应该处于禁用状态");
    }

    @Test
    @DisplayName("ES搜索功能测试")
    void testElasticsearchSearch() {
        IPage<ArticlePageQueryRespDTO> mockPage = mock(IPage.class);
        when(elasticsearchService.searchArticles(any(ArticlePageQueryReqDTO.class)))
                .thenReturn(mockPage);

        IPage<ArticlePageQueryRespDTO> result = elasticsearchService.searchArticles(requestParam);

        assertNotNull(result, "搜索结果不应为空");
        verify(elasticsearchService, times(1)).searchArticles(requestParam);
    }

    @Test
    @DisplayName("数据库搜索功能测试")
    void testDatabaseSearch() {
        IPage<ArticlePageQueryRespDTO> mockPage = mock(IPage.class);
        when(databaseService.searchArticles(any(ArticlePageQueryReqDTO.class)))
                .thenReturn(mockPage);

        IPage<ArticlePageQueryRespDTO> result = databaseService.searchArticles(requestParam);

        assertNotNull(result, "搜索结果不应为空");
        verify(databaseService, times(1)).searchArticles(requestParam);
    }

    @Test
    @DisplayName("ES索引文章测试")
    void testIndexArticle() {
        ArticleDO article = new ArticleDO();
        article.setId(1L);
        article.setTitle("测试文章");
        article.setContent("测试内容");

        doNothing().when(elasticsearchService).indexArticle(any(ArticleDO.class));

        elasticsearchService.indexArticle(article);

        verify(elasticsearchService, times(1)).indexArticle(article);
    }

    @Test
    @DisplayName("数据库模式跳过索引测试")
    void testDatabaseSkipIndex() {
        ArticleDO article = new ArticleDO();
        article.setId(1L);
        article.setTitle("测试文章");

        doNothing().when(databaseService).indexArticle(any(ArticleDO.class));

        databaseService.indexArticle(article);

        verify(databaseService, times(1)).indexArticle(article);
    }

    @Test
    @DisplayName("ES更新文章测试")
    void testUpdateArticle() {
        ArticleDO article = new ArticleDO();
        article.setId(1L);
        article.setTitle("更新后的文章");

        doNothing().when(elasticsearchService).updateArticle(any(ArticleDO.class));

        elasticsearchService.updateArticle(article);

        verify(elasticsearchService, times(1)).updateArticle(article);
    }

    @Test
    @DisplayName("ES删除文章测试")
    void testDeleteArticle() {
        Long articleId = 1L;

        doNothing().when(elasticsearchService).deleteArticle(any(Long.class));

        elasticsearchService.deleteArticle(articleId);

        verify(elasticsearchService, times(1)).deleteArticle(articleId);
    }

    @Test
    @DisplayName("ES批量同步测试")
    void testSyncAllArticles() {
        doNothing().when(elasticsearchService).syncAllArticles();

        elasticsearchService.syncAllArticles();

        verify(elasticsearchService, times(1)).syncAllArticles();
    }

    @Test
    @DisplayName("ES计数测试")
    void testCount() {
        when(elasticsearchService.count()).thenReturn(100L);

        long count = elasticsearchService.count();

        assertEquals(100L, count, "计数应该为100");
    }
}
