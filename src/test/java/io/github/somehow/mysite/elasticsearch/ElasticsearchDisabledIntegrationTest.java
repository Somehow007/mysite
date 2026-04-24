package io.github.somehow.mysite.elasticsearch;

import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.UserSyncService;
import io.github.somehow.mysite.service.impl.DatabaseArticleSearchServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "elasticsearch.enabled=false"
})
@DisplayName("ES禁用状态集成测试")
class ElasticsearchDisabledIntegrationTest {

    @Autowired(required = false)
    private ElasticsearchDataInitializer dataInitializer;

    @Autowired
    private ArticleSearchService articleSearchService;

    @Autowired
    private UserSyncService userSyncService;

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Test
    @DisplayName("ES禁用时数据初始化器不应该被加载")
    void testDataInitializerNotLoaded() {
        assertNull(dataInitializer, "ES禁用时，数据初始化器不应该被加载");
    }

    @Test
    @DisplayName("ES禁用时应该使用数据库实现")
    void testDatabaseImplementationLoaded() {
        assertTrue(articleSearchService instanceof DatabaseArticleSearchServiceImpl,
                "ES禁用时，应该使用DatabaseArticleSearchServiceImpl");
    }

    @Test
    @DisplayName("ES禁用状态验证")
    void testElasticsearchDisabled() {
        assertFalse(elasticsearchProperties.isEnabled(), "ES应该处于禁用状态");
        assertFalse(articleSearchService.isEnabled(), "搜索服务应该报告ES已禁用");
    }
}
