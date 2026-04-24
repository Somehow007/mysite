package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.config.ElasticsearchProperties;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.UserEsRepository;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.elasticsearch.UserDocument;
import io.github.somehow.mysite.service.UserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchUserSyncServiceImpl implements UserSyncService {

    private final UserEsRepository userEsRepository;
    private final UserMapper userMapper;
    private final ElasticsearchProperties elasticsearchProperties;

    @Override
    public void syncUser(UserDO user) {
        log.info("[ES同步] 同步用户: id={}, username={}", user.getId(), user.getUsername());
        UserDocument doc = convertToDocument(user);
        userEsRepository.save(doc);
        log.debug("[ES同步] 用户同步成功: id={}", user.getId());
    }

    @Override
    public void deleteUser(Long userId) {
        log.info("[ES删除] 删除用户索引: id={}", userId);
        userEsRepository.deleteById(userId.toString());
        log.debug("[ES删除] 用户索引删除成功: id={}", userId);
    }

    @Override
    public void syncAllUsers() {
        log.info("[ES同步] 开始同步所有用户到Elasticsearch...");
        var users = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getDelFlag, 0)
                        .orderByDesc(UserDO::getCreateTime));

        if (users.isEmpty()) {
            log.warn("[ES同步] 数据库中没有用户数据");
            return;
        }

        var documents = users.stream()
                .map(this::convertToDocument)
                .collect(java.util.stream.Collectors.toList());

        userEsRepository.saveAll(documents);
        log.info("[ES同步] 成功同步 {} 个用户到Elasticsearch", documents.size());
    }

    @Override
    public boolean isEnabled() {
        return elasticsearchProperties.isEnabled();
    }

    private UserDocument convertToDocument(UserDO user) {
        return UserDocument.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .realName(user.getRealName())
                .sex(user.getSex())
                .followingCount(user.getFollowingCount())
                .followerCount(user.getFollowerCount())
                .createTime(user.getCreateTime())
                .build();
    }
}
