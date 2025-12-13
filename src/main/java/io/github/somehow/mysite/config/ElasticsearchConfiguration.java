package io.github.somehow.mysite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 配置类
 * todo：后续考虑可以加上 canal 对数据进行同步，或采用 mq 进行异步操作
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "io.github.somehow.mysite.dao.mapper")
public class ElasticsearchConfiguration {
}