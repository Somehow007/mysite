package io.github.somehow.mysite.elasticsearch;

import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.UserSyncService;
import io.github.somehow.mysite.service.impl.ElasticsearchArticleSearchServiceImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("需要运行 Elasticsearch 服务")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "elasticsearch.enabled=true"
})
@DisplayName("ES启用状态集成测试")
class ElasticsearchEnabledIntegrationTest {

    @Autowired(required = false)
    private ElasticsearchDataInitializer dataInitializer;

    @Autowired
    private ArticleSearchService articleSearchService;

    @Autowired
    private UserSyncService userSyncService;

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Test
    @DisplayName("ES启用时数据初始化器应该被加载")
    void testDataInitializerLoaded() {
        assertNotNull(dataInitializer, "ES启用时，数据初始化器应该被加载");
    }

    @Test
    @DisplayName("ES启用时应该使用ES实现")
    void testElasticsearchImplementationLoaded() {
        assertTrue(articleSearchService instanceof ElasticsearchArticleSearchServiceImpl,
                "ES启用时，应该使用ElasticsearchArticleSearchServiceImpl");
    }

    @Test
    @DisplayName("ES启用状态验证")
    void testElasticsearchEnabled() {
        assertTrue(elasticsearchProperties.isEnabled(), "ES应该处于启用状态");
        assertTrue(articleSearchService.isEnabled(), "搜索服务应该报告ES已启用");
    }
}
