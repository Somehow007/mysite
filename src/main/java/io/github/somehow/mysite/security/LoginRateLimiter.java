package io.github.somehow.mysite.security;

import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录限流：IP 维度固定窗口计数，超限拒绝。
 * 在线爆破防御——此前 /v1/auth/login 无频次限制（risks.md R4）。
 * 模式对齐 {@code ChatRateLimiter}（Redis increment + 首次设 TTL）。
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${auth.login.max-attempts:10}")
    private int maxAttempts;

    @Value("${auth.login.window-minutes:10}")
    private int windowMinutes;

    public LoginRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @throws ClientException OPERATION_TOO_FREQUENT 超限
     */
    public void check(String clientIp) {
        String key = "auth:login:rl:" + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
        }
        if (count != null && count > maxAttempts) {
            log.warn("[login-rate-limit] IP={} exceeded: {}/{}", clientIp, count, maxAttempts);
            throw new ClientException(ErrorCode.OPERATION_TOO_FREQUENT);
        }
    }
}
