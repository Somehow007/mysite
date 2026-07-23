package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.ragent.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 聊天限流器 —— 角色感知，三层速率。
 *
 * 策略：
 *   ADMIN  → 无限制（adminMaxPerHour == 0 时跳过计数）
 *   CREATOR → 20次/小时（Redis IP 维度计数）
 *   USER    → 10次/小时（Redis IP 维度计数）
 *   未登录  → WebSecurityConfig 已拦截为 401，本类作为双保险拒绝
 */
@Slf4j
@Component
public class ChatRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RagProperties properties;

    public ChatRateLimiter(StringRedisTemplate redisTemplate, RagProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 角色感知限流检查。
     *
     * @param clientIp 客户端 IP
     * @param question 用户问题
     * @param role     当前用户角色（null = 未登录，直接拒绝）
     * @throws RateLimitExceededException 超出限制时抛出
     */
    public void check(String clientIp, String question, UserRole role) {
        if (role == null) {
            throw new RateLimitExceededException("请登录后使用 AI 助手");
        }

        int maxLength = properties.getRateLimit().getMaxQuestionLength();

        // 1. 长度检查（所有角色统一）
        if (question != null && question.length() > maxLength) {
            throw new RateLimitExceededException(
                "问题过长（" + question.length() + " 字符），请控制在 " + maxLength + " 字符以内");
        }

        // 2. 获取角色对应的限流阈值
        int maxPerHour = getMaxPerHour(role);

        // 3. ADMIN 不限流（0 = unlimited）
        if (maxPerHour <= 0) {
            log.debug("[rate-limit] ADMIN {} skipped (unlimited)", clientIp);
            return;
        }

        // 4. IP 维度 Redis 计数
        String key = "rag:chat:rl:" + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        if (count != null && count > maxPerHour) {
            log.warn("[rate-limit] IP={} role={} exceeded: {}/{}", clientIp, role, count, maxPerHour);
            throw new RateLimitExceededException(
                "提问太频繁（" + role.getDescription() + "每小时 " + maxPerHour + " 次），请稍后再试");
        }
        log.debug("[rate-limit] IP={} role={} count={}/{}", clientIp, role, count, maxPerHour);
    }

    private int getMaxPerHour(UserRole role) {
        return switch (role) {
            case ADMIN, DEVELOPER -> properties.getRateLimit().getAdminMaxPerHour();
            case CREATOR -> properties.getRateLimit().getCreatorMaxPerHour();
            case USER -> properties.getRateLimit().getUserMaxPerHour();
        };
    }

    /**
     * 限流异常 —— Controller 层捕获后转为 ChatEvent.error。
     */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
