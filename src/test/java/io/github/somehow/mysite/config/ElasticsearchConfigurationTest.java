package io.github.somehow.mysite.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Elasticsearch配置测试")
class ElasticsearchConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private ElasticsearchProperties elasticsearchProperties;

    @Test
    @DisplayName("配置属性加载测试")
    void testPropertiesLoaded() {
        assertNotNull(elasticsearchProperties, "配置属性应该被加载");
    }

    @Test
    @DisplayName("默认启用状态测试")
    void testDefaultEnabled() {
        if (elasticsearchProperties != null) {
            assertTrue(elasticsearchProperties.isEnabled(), "默认应该启用ES");
        }
    }

    @Test
    @DisplayName("ES URIs配置测试")
    void testUrisConfiguration() {
        if (elasticsearchProperties != null) {
            assertNotNull(elasticsearchProperties.getUris(), "URIs配置不应为空");
            assertTrue(elasticsearchProperties.getUris().length > 0, "至少应该有一个URI");
        }
    }

    @Test
    @DisplayName("超时配置测试")
    void testTimeoutConfiguration() {
        if (elasticsearchProperties != null) {
            assertTrue(elasticsearchProperties.getConnectionTimeout() > 0, 
                    "连接超时应该大于0");
            assertTrue(elasticsearchProperties.getSocketTimeout() > 0, 
                    "Socket超时应该大于0");
        }
    }
}
