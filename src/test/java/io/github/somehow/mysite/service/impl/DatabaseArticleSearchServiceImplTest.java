package io.github.somehow.mysite.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.CategoryMapper;
import io.github.somehow.mysite.dao.mapper.CollectionArticleMapper;
import io.github.somehow.mysite.dao.mapper.CollectionMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.article.ArticlePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.utils.ReadingTimeCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据库搜索服务 - readingTime传递测试")
class DatabaseArticleSearchServiceImplTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ArticleTagMapper articleTagMapper;

    @Mock
    private UserFavoriteArticleMapper userFavoriteArticleMapper;

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private CollectionArticleMapper collectionArticleMapper;

    @Mock
    private ElasticsearchProperties elasticsearchProperties;

    @InjectMocks
    private DatabaseArticleSearchServiceImpl searchService;

    private ArticlePageQueryReqDTO requestParam;

    @BeforeEach
    void setUp() {
        requestParam = new ArticlePageQueryReqDTO();
        requestParam.setCurrent(1L);
        requestParam.setSize(10L);
    }

    private ArticleDO createArticleDO(Long id, String content, Integer readingTime) {
        ArticleDO article = new ArticleDO();
        article.setId(id);
        article.setTitle("测试文章" + id);
        article.setContent(content);
        article.setSummary("摘要");
        article.setAuthorId(1L);
        article.setCategoryId(null);
        article.setViewCount(10);
        article.setFavoriteCount(2);
        article.setReadingTime(readingTime);
        article.setCreateTime(new Date());
        article.setUpdateTime(new Date());
        article.setDelFlag(0);
        article.setPublished(1);
        return article;
    }

    @Test
    @DisplayName("搜索结果应包含readingTime字段")
    void searchArticles_shouldIncludeReadingTime() {
        ArticleDO article = createArticleDO(1L, "测试内容", 5);

        Page<ArticleDO> articlePage = new Page<>(1, 10, 1);
        articlePage.setRecords(List.of(article));
        when(articleMapper.selectPage(any(), any())).thenReturn(articlePage);

        UserDO author = new UserDO();
        author.setId(1L);
        author.setUsername("testuser");
        when(userMapper.selectList(any())).thenReturn(List.of(author));

        IPage<ArticlePageQueryRespDTO> result = searchService.searchArticles(requestParam);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        ArticlePageQueryRespDTO dto = result.getRecords().get(0);
        assertNotNull(dto.getReadingTime(), "readingTime不应为null");
        assertEquals(5, dto.getReadingTime(), "readingTime应与ArticleDO中的值一致");
    }

    @Test
    @DisplayName("readingTime应与ReadingTimeCalculator计算结果一致")
    void searchArticles_readingTimeShouldMatchCalculator() {
        String content = "这是一篇测试文章，用来验证阅读时间计算是否正确。".repeat(20);
        int expectedTime = ReadingTimeCalculator.calculate(content);

        ArticleDO article = createArticleDO(1L, content, expectedTime);

        Page<ArticleDO> articlePage = new Page<>(1, 10, 1);
        articlePage.setRecords(List.of(article));
        when(articleMapper.selectPage(any(), any())).thenReturn(articlePage);

        UserDO author = new UserDO();
        author.setId(1L);
        author.setUsername("testuser");
        when(userMapper.selectList(any())).thenReturn(List.of(author));

        IPage<ArticlePageQueryRespDTO> result = searchService.searchArticles(requestParam);

        ArticlePageQueryRespDTO dto = result.getRecords().get(0);
        assertEquals(expectedTime, dto.getReadingTime(),
                "DTO中的readingTime应与ReadingTimeCalculator计算结果一致");
    }

    @Test
    @DisplayName("多篇文章各自的readingTime应正确传递")
    void searchArticles_multipleArticles_readingTimeShouldBeCorrect() {
        ArticleDO article1 = createArticleDO(1L, "短内容", 1);
        ArticleDO article2 = createArticleDO(2L, "长内容", 8);

        Page<ArticleDO> articlePage = new Page<>(1, 10, 2);
        articlePage.setRecords(List.of(article1, article2));
        when(articleMapper.selectPage(any(), any())).thenReturn(articlePage);

        UserDO author = new UserDO();
        author.setId(1L);
        author.setUsername("testuser");
        when(userMapper.selectList(any())).thenReturn(List.of(author));

        IPage<ArticlePageQueryRespDTO> result = searchService.searchArticles(requestParam);

        assertEquals(2, result.getRecords().size());
        assertEquals(1, result.getRecords().get(0).getReadingTime());
        assertEquals(8, result.getRecords().get(1).getReadingTime());
    }

    @Test
    @DisplayName("空搜索结果不应抛出异常")
    void searchArticles_emptyResult_shouldNotThrow() {
        Page<ArticleDO> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(Collections.emptyList());
        when(articleMapper.selectPage(any(), any())).thenReturn(emptyPage);

        IPage<ArticlePageQueryRespDTO> result = searchService.searchArticles(requestParam);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("readingTime为1时也应正确传递")
    void searchArticles_readingTimeIsOne_shouldPassThrough() {
        ArticleDO article = createArticleDO(1L, "短", 1);

        Page<ArticleDO> articlePage = new Page<>(1, 10, 1);
        articlePage.setRecords(List.of(article));
        when(articleMapper.selectPage(any(), any())).thenReturn(articlePage);

        UserDO author = new UserDO();
        author.setId(1L);
        author.setUsername("testuser");
        when(userMapper.selectList(any())).thenReturn(List.of(author));

        IPage<ArticlePageQueryRespDTO> result = searchService.searchArticles(requestParam);

        assertEquals(1, result.getRecords().get(0).getReadingTime());
    }

    @Test
    @DisplayName("包含代码块的文章readingTime应正确传递")
    void searchArticles_articleWithCodeBlock_readingTimeShouldPass() {
        String content = "## 代码示例\n\n```java\nSystem.out.println(\"hello\");\n```\n\n这是说明文字";
        int expectedTime = ReadingTimeCalculator.calculate(content);

        ArticleDO article = createArticleDO(1L, content, expectedTime);

        Page<ArticleDO> articlePage = new Page<>(1, 10, 1);
        articlePage.setRecords(List.of(article));
        when(articleMapper.selectPage(any(), any())).thenReturn(articlePage);

        UserDO author = new UserDO();
        author.setId(1L);
        author.setUsername("testuser");
        when(userMapper.selectList(any())).thenReturn(List.of(author));

        IPage<ArticlePageQueryRespDTO> result = searchService.searchArticles(requestParam);

        assertEquals(expectedTime, result.getRecords().get(0).getReadingTime());
    }
}
