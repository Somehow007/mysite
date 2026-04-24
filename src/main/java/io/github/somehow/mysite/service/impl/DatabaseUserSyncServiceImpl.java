package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.service.UserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", havingValue = "false")
public class DatabaseUserSyncServiceImpl implements UserSyncService {

    private final ElasticsearchProperties elasticsearchProperties;

    @Override
    public void syncUser(UserDO user) {
        log.debug("[数据库模式] 跳过同步用户: id={}, Elasticsearch已禁用", user.getId());
    }

    @Override
    public void deleteUser(Long userId) {
        log.debug("[数据库模式] 跳过删除用户索引: id={}, Elasticsearch已禁用", userId);
    }

    @Override
    public void syncAllUsers() {
        log.info("[数据库模式] Elasticsearch已禁用，跳过用户数据同步");
    }

    @Override
    public boolean isEnabled() {
        return elasticsearchProperties.isEnabled();
    }
}
