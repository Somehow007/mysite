package io.github.somehow.mysite.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Slf4j
@Configuration
@EnableElasticsearchRepositories(basePackages = "io.github.somehow.mysite.dao.mapper")
@ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchConfiguration {

    public ElasticsearchConfiguration() {
        log.info("========================================");
        log.info("Elasticsearch 已启用");
        log.info("ES Repositories 已加载");
        log.info("========================================");
    }
}
