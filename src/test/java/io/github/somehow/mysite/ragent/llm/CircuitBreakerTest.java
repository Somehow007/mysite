package io.github.somehow.mysite.ragent.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CircuitBreaker 三态断路器单元测试。
 *
 * 覆盖验收清单：
 *   ✅ 三个状态切换逻辑正确（含 HALF_OPEN 探测失败重回 OPEN）
 *
 * 状态转换图：
 *   CLOSED ──连续失败≥threshold──▶ OPEN
 *   OPEN   ──冷却时间到──────────▶ HALF_OPEN
 *   HALF_OPEN ──探测成功────────▶ CLOSED
 *   HALF_OPEN ──探测失败────────▶ OPEN（重新冷却）
 */
@DisplayName("CircuitBreaker 三态断路器")
class CircuitBreakerTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long COOLDOWN_SECONDS = 1; // 1 秒冷却快速测试
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000 + 200; // 冷却等待时间

    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new CircuitBreaker("test-breaker", FAILURE_THRESHOLD, COOLDOWN_SECONDS);
    }

    @Nested
    @DisplayName("初始状态")
    class InitialState {

        @Test
        @DisplayName("断路器初始状态为 CLOSED")
        void shouldStartInClosedState() {
            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        }

        @Test
        @DisplayName("CLOSED 状态下允许所有请求通过")
        void shouldAllowRequestsWhenClosed() {
            for (int i = 0; i < 100; i++) {
                assertTrue(breaker.allowRequest(), "CLOSED 状态应该允许所有请求通过");
            }
        }
    }

    @Nested
    @DisplayName("CLOSED → OPEN 转换")
    class ClosedToOpen {

        @Test
        @DisplayName("失败次数未达阈值时不熔断")
        void shouldNotOpenBeforeThreshold() {
            breaker.recordFailure();
            breaker.recordFailure(); // 仅 2 次，阈值为 3

            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
            assertTrue(breaker.allowRequest());
        }

        @Test
        @DisplayName("连续失败达到阈值后进入 OPEN 状态")
        void shouldOpenAfterThresholdFailures() {
            breaker.recordFailure(); // 1
            breaker.recordFailure(); // 2
            breaker.recordFailure(); // 3 → 触发熔断

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        }

        @Test
        @DisplayName("OPEN 状态下拒绝所有请求")
        void shouldRejectRequestsWhenOpen() {
            // 触发熔断
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }

            for (int i = 0; i < 10; i++) {
                assertFalse(breaker.allowRequest(), "OPEN 状态应拒绝请求");
            }
        }

        @Test
        @DisplayName("isOpen() 在 OPEN 状态返回 true")
        void isOpenShouldReturnTrueWhenOpen() {
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }

            assertTrue(breaker.isOpen());
        }
    }

    @Nested
    @DisplayName("OPEN → HALF_OPEN 转换")
    class OpenToHalfOpen {

        @Test
        @DisplayName("冷却时间到后允许一个探测请求通过")
        void shouldAllowOneProbeAfterCooldown() throws InterruptedException {
            // 触发熔断
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }
            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

            // 等待冷却结束
            Thread.sleep(COOLDOWN_MILLIS + 50);

            // 第一个请求：获得探测权，状态转为 HALF_OPEN
            assertTrue(breaker.allowRequest(), "冷却后应允许一个探测请求");
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());
        }

        @Test
        @DisplayName("冷却时间内仍拒绝请求")
        void shouldRejectDuringCooldown() {
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }

            // 冷却未到（COOLDOWN_MILLIS = 200，刚过几毫秒）
            assertFalse(breaker.allowRequest(), "冷却时间内应拒绝请求");
            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        }

        @Test
        @DisplayName("HALF_OPEN 状态下拒绝后续请求（只放行一个探测）")
        void shouldRejectOtherRequestsInHalfOpen() throws InterruptedException {
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }

            Thread.sleep(COOLDOWN_MILLIS + 50);

            // 第一个拿到探测权
            assertTrue(breaker.allowRequest());
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

            // 后续请求被拒绝
            assertFalse(breaker.allowRequest(), "HALF_OPEN 应只放一个探测请求");
            assertFalse(breaker.allowRequest());
        }
    }

    @Nested
    @DisplayName("HALF_OPEN 恢复 / 重新熔断")
    class HalfOpenRecovery {

        @Test
        @DisplayName("探测成功 → 回到 CLOSED，失败计数清零")
        void shouldRecoverToClosedOnSuccess() throws InterruptedException {
            // 触发熔断
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }

            Thread.sleep(COOLDOWN_MILLIS + 50);

            // 探测请求
            assertTrue(breaker.allowRequest());
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

            // 探测成功
            breaker.recordSuccess();

            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
            // 失败计数应清零：再失败一次不应触发熔断
            breaker.recordFailure();
            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        }

        @Test
        @DisplayName("探测失败 → 回到 OPEN，重新计时")
        void shouldReopenOnProbeFailure() throws InterruptedException {
            // 触发熔断
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }

            Thread.sleep(COOLDOWN_MILLIS + 50);

            // 探测请求
            assertTrue(breaker.allowRequest());
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

            // 探测失败
            breaker.recordFailure();

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState(), "HALF_OPEN 探测失败应回到 OPEN");

            // 应该需要重新等冷却
            assertFalse(breaker.allowRequest(), "重新熔断后应立即拒绝请求");
        }
    }

    @Nested
    @DisplayName("并发安全")
    class Concurrency {

        @Test
        @DisplayName("多个线程同时请求探测权，只有一个获得")
        void onlyOneThreadGetsProbe() throws Exception {
            // 触发熔断
            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }

            // 等待冷却结束
            Thread.sleep(COOLDOWN_MILLIS + 50);

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger probesGranted = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    if (breaker.allowRequest()) {
                        probesGranted.incrementAndGet();
                    }
                    latch.countDown();
                });
            }

            latch.await(2, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(1, probesGranted.get(),
                "CAS 保证只有一个线程获得 HALF_OPEN 探测权");
        }
    }

    @Nested
    @DisplayName("健康状态查询")
    class HealthStatus {

        @Test
        @DisplayName("getState() 返回当前状态")
        void shouldReturnCurrentState() {
            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());

            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }
            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        }

        @Test
        @DisplayName("isOpen() 仅在 OPEN 时返回 true")
        void isOpenShouldOnlyBeTrueForOpen() throws InterruptedException {
            assertFalse(breaker.isOpen());

            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                breaker.recordFailure();
            }
            assertTrue(breaker.isOpen());

            Thread.sleep(COOLDOWN_MILLIS + 50);
            breaker.allowRequest(); // 进入 HALF_OPEN
            assertFalse(breaker.isOpen());

            breaker.recordSuccess(); // 回到 CLOSED
            assertFalse(breaker.isOpen());
        }
    }
}
