package io.github.somehow.mysite.elasticsearch;

import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.elasticsearch.repository.ArticleEsRepository;
import io.github.somehow.mysite.elasticsearch.repository.UserEsRepository;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.service.ArticleSearchService;
import io.github.somehow.mysite.service.UserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchDataInitializer implements CommandLineRunner {

    private final ArticleSearchService articleSearchService;
    private final UserSyncService userSyncService;
    private final ElasticsearchProperties elasticsearchProperties;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("开始 Elasticsearch 数据初始化检查...");
        log.info("ES 状态: 已启用");
        log.info("========================================");

        checkAndSyncArticles();
        checkAndSyncUsers();

        log.info("========================================");
        log.info("Elasticsearch 数据初始化完成");
        log.info("========================================");
    }

    private void checkAndSyncArticles() {
        try {
            long esCount = articleSearchService.count();
            log.info("[文章数据检查] ES索引文档数: {}", esCount);

            if (esCount == 0) {
                log.warn("[文章数据检查] ES文章索引为空，开始从数据库同步数据...");
                articleSearchService.syncAllArticles();
            } else {
                log.info("[文章数据检查] ES索引已存在数据，跳过同步");
            }
        } catch (Exception e) {
            log.error("[文章数据检查] 数据同步失败: {}", e.getMessage(), e);
        }
    }

    private void checkAndSyncUsers() {
        try {
            log.info("[用户数据检查] 开始检查用户数据...");
            userSyncService.syncAllUsers();
            log.info("[用户数据检查] 用户数据检查完成");
        } catch (Exception e) {
            log.error("[用户数据检查] 数据同步失败: {}", e.getMessage(), e);
        }
    }
}
