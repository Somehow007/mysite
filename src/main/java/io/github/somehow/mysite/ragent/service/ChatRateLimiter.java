package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.ragent.config.RagProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 聊天限流器 —— 成本保护的第一道闸。
 *
 * 为什么需要限流？
 *   RAG 问答 = embedding API + 向量检索 + LLM 流式生成，
 *   其中 embedding 和 LLM 都是按 token 计费的云 API。
 *   permitAll 端点不做限流，恶意脚本一晚上就能烧掉几百块。
 *
 * 策略（Redis 固定窗口，够用不复杂）：
 *   1. IP 维度计数：key = rag:chat:rl:{ip}，INCR + 首值设 1 小时 TTL
 *   2. 问题长度上限：默认 500 字符，超长直接拒绝
 *
 * Phase 4 可升级为滑动窗口 / Token Bucket（精度更高但 Redis 脚本更复杂）。
 */
@Component
public class ChatRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RagProperties properties;

    public ChatRateLimiter(StringRedisTemplate redisTemplate, RagProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 检查请求是否超出频率/长度限制。
     *
     * @param clientIp 客户端 IP
     * @param question 用户问题
     * @throws RateLimitExceededException 超出限制时抛出
     */
    public void check(String clientIp, String question) {
        int maxPerHour = properties.getRateLimit().getMaxPerHour();
        int maxLength = properties.getRateLimit().getMaxQuestionLength();

        // 1. 长度检查
        if (question != null && question.length() > maxLength) {
            throw new RateLimitExceededException(
                "问题过长（" + question.length() + " 字符），请控制在 " + maxLength + " 字符以内");
        }

        // 2. IP 限流（Redis INCR + 首次设 TTL）
        String key = "rag:chat:rl:" + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        if (count != null && count > maxPerHour) {
            throw new RateLimitExceededException(
                "请求过于频繁，每小时限制 " + maxPerHour + " 次，请稍后再试");
        }
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
