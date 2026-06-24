package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleViewCountService {

    private static final String VIEW_COUNT_KEY_PREFIX = "article:view_count:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;

    public void incrementViewCount(Long articleId) {
        try {
            stringRedisTemplate.opsForValue().increment(VIEW_COUNT_KEY_PREFIX + articleId);
        } catch (Exception e) {
            log.warn("Redis 浏览量递增失败, articleId={}", articleId, e);
        }
    }

    public long getPendingViewCount(Long articleId) {
        try {
            String val = stringRedisTemplate.opsForValue().get(VIEW_COUNT_KEY_PREFIX + articleId);
            return val != null ? Long.parseLong(val) : 0;
        } catch (Exception e) {
            log.warn("获取 Redis 浏览量失败, articleId={}", articleId, e);
            return 0;
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void flushViewCounts() {
        try {
            Set<String> keys = stringRedisTemplate.keys(VIEW_COUNT_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            for (String key : keys) {
                String countStr = stringRedisTemplate.opsForValue().getAndSet(key, "0");
                if (countStr != null) {
                    long count = Long.parseLong(countStr);
                    if (count > 0) {
                        String articleIdStr = key.substring(VIEW_COUNT_KEY_PREFIX.length());
                        articleMapper.incrementViewCount(Long.parseLong(articleIdStr), (int) count);
                        log.debug("刷新浏览量: articleId={}, +{}", articleIdStr, count);
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量刷新浏览量失败", e);
        }
    }
}
