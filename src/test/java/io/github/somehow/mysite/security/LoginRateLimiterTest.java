package io.github.somehow.mysite.security;

import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 登录限流器 characterization：IP 维度固定窗口，超限抛 OPERATION_TOO_FREQUENT。
 */
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(limiter, "maxAttempts", 10);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 10);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void firstAttempt_shouldSetWindowTtl() {
        when(valueOps.increment("auth:login:rl:1.2.3.4")).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.check("1.2.3.4"));

        verify(redisTemplate).expire("auth:login:rl:1.2.3.4", Duration.ofMinutes(10));
    }

    @Test
    void withinLimit_shouldPass() {
        when(valueOps.increment("auth:login:rl:1.2.3.4")).thenReturn(10L);

        assertDoesNotThrow(() -> limiter.check("1.2.3.4"));
        verify(redisTemplate, never()).expire(eq("auth:login:rl:1.2.3.4"), any(Duration.class));
    }

    @Test
    void exceedingLimit_shouldThrowTooFrequent() {
        when(valueOps.increment("auth:login:rl:1.2.3.4")).thenReturn(11L);

        ClientException ex = assertThrows(ClientException.class,
                () -> limiter.check("1.2.3.4"));
        assertEquals(ErrorCode.OPERATION_TOO_FREQUENT.code(), ex.getErrorCode());
    }

    @Test
    void nullCount_shouldPassGracefully() {
        when(valueOps.increment("auth:login:rl:1.2.3.4")).thenReturn(null);

        assertDoesNotThrow(() -> limiter.check("1.2.3.4"));
    }

    @Test
    void differentIps_shouldCountIndependently() {
        when(valueOps.increment("auth:login:rl:1.1.1.1")).thenReturn(11L);
        when(valueOps.increment("auth:login:rl:2.2.2.2")).thenReturn(1L);

        assertThrows(ClientException.class, () -> limiter.check("1.1.1.1"));
        assertDoesNotThrow(() -> limiter.check("2.2.2.2"));
    }
}
