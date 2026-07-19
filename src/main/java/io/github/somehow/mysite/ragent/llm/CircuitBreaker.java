package io.github.somehow.mysite.ragent.llm;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 三态断路器
 * 状态转换逻辑：
 *   CLOSED ──连续失败≥threshold──▶ OPEN
 *   OPEN   ──冷却时间到──────────▶ HALF_OPEN
 *   HALF_OPEN ──探测成功────────▶ CLOSED
 *   HALF_OPEN ──探测失败────────▶ OPEN (重新冷却)
 * 要点：
 *   1. 为什么用三态而不是两态？HALF_OPEN 避免了"冷却后立即全量请求
 *      再次打垮服务"的问题 —— 只放一个请求去探测
 *   2. 为什么用 AtomicReference 而不是 synchronized？
 *      断路器操作是轻量级的读-改-写，CAS 比锁更高效
 */
public class CircuitBreaker {

    /**
     * public：路由器的健康检查（getHealthStatus）需要向外暴露这个类型
     */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;     // 连续失败 N 次 → 熔断
    private final long cooldownMillis;      // 冷却时间（毫秒）
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openedAt = 0;     // 进入 OPEN 状态的时间戳

    public CircuitBreaker(String name, int failureThreshold, long cooldownSeconds) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldownMillis = cooldownSeconds * 1000;
    }

    /**
     * 调用前检查：是否允许请求通过？
     * CLOSED -> true
     * OPEN + 冷却中 -> false
     * OPEN + 冷却完 -> 转为 HALF_OPEN，放心一个探测请求
     * HALF_OPEN -> false（只放行一个）
     */
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            // 检查是否冷却完毕
            if (System.currentTimeMillis() - openedAt >= cooldownMillis) {
                // CAS 保证只有一个线程能从 OPEN 转到 HALF_OPEN
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    return true;    // 该线程获得探测权
                }
            }
            return false;
        }
        // HALF_OPEN 已有探测请求在外面，其他请求不通过
        return false;
    }

    /**
     * 调用成功后回调
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);         // HALF_OPEN 探测成功 → 恢复
    }

    /**
     * 调用失败后回调
     */
    public void recordFailure() {
        // HALF_OPEN 状态下的失败：探测没通过，直接重新熔断并重新计时
        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
        }
    }

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    public State getState() {
        return state.get();
    }
}
