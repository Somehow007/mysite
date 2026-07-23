package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.ragent.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChatRateLimiter — 限流器")
class ChatRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RagProperties properties;
    private ChatRateLimiter rateLimiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        properties = new RagProperties();
        properties.getRateLimit().setMaxPerHour(20);
        properties.getRateLimit().setMaxQuestionLength(500);

        rateLimiter = new ChatRateLimiter(redisTemplate, properties);
    }

    @Nested
    @DisplayName("长度检查")
    class LengthCheck {

        @Test
        @DisplayName("正常长度 → 通过")
        void normalLengthShouldPass() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            assertDoesNotThrow(() -> rateLimiter.check("127.0.0.1", "正常问题", UserRole.USER));
        }

        @Test
        @DisplayName("超长 → 抛出异常")
        void tooLongShouldThrow() {
            String longQ = "x".repeat(501);
            ChatRateLimiter.RateLimitExceededException ex = assertThrows(
                ChatRateLimiter.RateLimitExceededException.class,
                () -> rateLimiter.check("127.0.0.1", longQ, UserRole.USER));
            assertTrue(ex.getMessage().contains("500"));
        }
    }

    @Nested
    @DisplayName("频率检查（Redis）")
    class RateCheck {

        @Test
        @DisplayName("首次请求 → 通过，设置 TTL")
        void firstRequestShouldPassAndSetTtl() {
            when(valueOps.increment("rag:chat:rl:127.0.0.1")).thenReturn(1L);

            assertDoesNotThrow(() -> rateLimiter.check("127.0.0.1", "问题", UserRole.USER));

            verify(redisTemplate).expire(eq("rag:chat:rl:127.0.0.1"), any(Duration.class));
        }

        @Test
        @DisplayName("未超限 → 通过，不重置 TTL")
        void withinLimitShouldPass() {
            when(valueOps.increment("rag:chat:rl:127.0.0.1")).thenReturn(10L);

            assertDoesNotThrow(() -> rateLimiter.check("127.0.0.1", "问题", UserRole.USER));

            verify(redisTemplate, never()).expire(anyString(), any());
        }

        @Test
        @DisplayName("超限 → 抛出异常")
        void exceedingLimitShouldThrow() {
            when(valueOps.increment("rag:chat:rl:127.0.0.1")).thenReturn(21L);

            ChatRateLimiter.RateLimitExceededException ex = assertThrows(
                ChatRateLimiter.RateLimitExceededException.class,
                () -> rateLimiter.check("127.0.0.1", "问题", UserRole.USER));
            assertTrue(ex.getMessage().contains("20"));
        }

        @Test
        @DisplayName("不同 IP 分别计数")
        void differentIpsShouldHaveSeparateCounters() {
            when(valueOps.increment(anyString())).thenReturn(1L);

            assertDoesNotThrow(() -> rateLimiter.check("1.2.3.4", "问题", UserRole.USER));
            assertDoesNotThrow(() -> rateLimiter.check("5.6.7.8", "问题", UserRole.USER));

            verify(valueOps).increment("rag:chat:rl:1.2.3.4");
            verify(valueOps).increment("rag:chat:rl:5.6.7.8");
        }
    }
}
