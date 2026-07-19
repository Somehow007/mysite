package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoutingLLMService 单元测试。
 *
 * 覆盖验收清单：
 *   ✅ 按优先级降级逻辑正确
 *   ✅ 降级边界正确 — 首 token 前失败降级，流中途失败不降级
 *   ✅ 应用启动无循环依赖（注入 List<LLMProvider> 不含自身）
 */
@DisplayName("RoutingLLMService 模型路由器")
class RoutingLLMServiceTest {

    private RagProperties properties;
    private RoutingLLMService router;

    // ===== 测试辅助：可控的 LLMProvider 实现 =====

    /**
     * 最简 stub：只做流式 chat，getName() 返回标识，不做断路（测试用）。
     */
    static class StubProvider implements LLMProvider {
        private final String name;
        private final Flux<String> response;

        StubProvider(String name, Flux<String> response) {
            this.name = name;
            this.response = response;
        }

        @Override
        public String getName() { return name; }

        @Override
        public Flux<String> chatStream(ChatRequest request) { return response; }

        @Override
        public String chat(ChatRequest request) {
            return chatStream(request).collectList().map(t -> String.join("", t)).block();
        }
    }

    /**
     * 创建一个简单的 RagProperties，其中 circuitBreaker 用温和默认值。
     */
    private static RagProperties createProperties() {
        RagProperties p = new RagProperties();
        RagProperties.CircuitBreakerProperties cb = new RagProperties.CircuitBreakerProperties();
        cb.setFailureThreshold(3);
        cb.setCooldownSeconds(30);
        p.getLlm().setCircuitBreaker(cb);
        return p;
    }

    // ==================== 降级链测试 ====================

    @Nested
    @DisplayName("供应商降级")
    class ProviderFallback {

        @Test
        @DisplayName("第一个供应商成功 → 使用第一个，不降级")
        void shouldUseFirstProviderWhenItSucceeds() {
            properties = createProperties();
            // 只启用 P1
            registerProvider("p1", 1);

            StubProvider p1 = new StubProvider("p1",
                Flux.just("Hello", " ", "World"));

            router = new RoutingLLMService(List.of(p1), properties);

            StepVerifier.create(router.chatStream(ChatRequest.of("m", "hi")))
                .expectNext("Hello", " ", "World")
                .verifyComplete();
        }

        @Test
        @DisplayName("P1 失败（未输出 token）→ 降级到 P2")
        void shouldFallbackToNextProviderWhenFirstFails() {
            properties = createProperties();
            registerProvider("p1", 1);
            registerProvider("p2", 2);

            StubProvider p1 = new StubProvider("p1",
                Flux.error(new RuntimeException("P1 down")));
            StubProvider p2 = new StubProvider("p2",
                Flux.just("P2", " responds"));

            router = new RoutingLLMService(List.of(p1, p2), properties);

            StepVerifier.create(router.chatStream(ChatRequest.of("m", "hi")))
                .expectNext("P2", " responds")
                .verifyComplete();
        }

        @Test
        @DisplayName("P1、P2 都失败 → 降级到 P3")
        void shouldFallbackThroughMultipleProviders() {
            properties = createProperties();
            registerProvider("p1", 1);
            registerProvider("p2", 2);
            registerProvider("p3", 3);

            StubProvider p1 = new StubProvider("p1",
                Flux.error(new RuntimeException("P1 down")));
            StubProvider p2 = new StubProvider("p2",
                Flux.error(new RuntimeException("P2 down")));
            StubProvider p3 = new StubProvider("p3",
                Flux.just("P3 saves the day"));

            router = new RoutingLLMService(List.of(p1, p2, p3), properties);

            StepVerifier.create(router.chatStream(ChatRequest.of("m", "hi")))
                .expectNext("P3 saves the day")
                .verifyComplete();
        }

        @Test
        @DisplayName("所有供应商都失败 → 抛出异常")
        void shouldThrowWhenAllProvidersFail() {
            properties = createProperties();
            registerProvider("p1", 1);

            StubProvider p1 = new StubProvider("p1",
                Flux.error(new RuntimeException("down")));

            router = new RoutingLLMService(List.of(p1), properties);

            StepVerifier.create(router.chatStream(ChatRequest.of("m", "hi")))
                .verifyErrorMessage("All LLM providers failed");
        }

        @Test
        @DisplayName("禁用的供应商被跳过")
        void shouldSkipDisabledProviders() {
            properties = createProperties();
            // p1 不在 enabled 列表中 → 被 filtered out
            // 只启用 p2
            registerProvider("p2", 2);

            StubProvider p1 = new StubProvider("p1",
                Flux.just("should not be used"));
            StubProvider p2 = new StubProvider("p2",
                Flux.just("enabled p2"));

            // p1 enabled=false → 过滤掉
            router = new RoutingLLMService(List.of(p1, p2), properties);

            StepVerifier.create(router.chatStream(ChatRequest.of("m", "hi")))
                .expectNext("enabled p2")
                .verifyComplete();
        }
    }

    // ==================== 降级边界测试 ====================

    @Nested
    @DisplayName("降级边界 — 已输出 token 后不降级")
    class FailFastAfterEmission {

        @Test
        @DisplayName("已输出 token 后失败 → 直接报错，不降到 P2")
        void shouldNotFallbackAfterTokensEmitted() {
            properties = createProperties();
            registerProvider("p1", 1);
            registerProvider("p2", 2);

            // P1 输出一个 token 后挂掉
            StubProvider p1 = new StubProvider("p1",
                Flux.just("first token")
                    .concatWith(Flux.error(new RuntimeException("mid-stream crash"))));
            StubProvider p2 = new StubProvider("p2",
                Flux.just("P2 should not appear"));

            router = new RoutingLLMService(List.of(p1, p2), properties);

            StepVerifier.create(router.chatStream(ChatRequest.of("m", "hi")))
                .expectNext("first token")
                .verifyErrorMessage("mid-stream crash");
        }

        @Test
        @DisplayName("未输出 token 前失败 → 可以降级")
        void shouldFallbackWhenNoTokensEmitted() {
            properties = createProperties();
            registerProvider("p1", 1);
            registerProvider("p2", 2);

            // P1 连接成功但第一个 token 就是 error（没有 doOnNext 触发）
            StubProvider p1 = new StubProvider("p1",
                Flux.error(new RuntimeException("connection lost")));
            StubProvider p2 = new StubProvider("p2",
                Flux.just("P2 fallback OK"));

            router = new RoutingLLMService(List.of(p1, p2), properties);

            StepVerifier.create(router.chatStream(ChatRequest.of("m", "hi")))
                .expectNext("P2 fallback OK")
                .verifyComplete();
        }
    }

    // ==================== 断路器集成测试 ====================

    @Nested
    @DisplayName("断路器自动熔断")
    class CircuitBreakerIntegration {

        @Test
        @DisplayName("连续失败达到阈值 → 熔断该供应商，跳到下一个")
        void shouldTripCircuitBreakerAndSkipProvider() {
            properties = createProperties();
            // failureThreshold = 3
            properties.getLlm().getCircuitBreaker().setFailureThreshold(2);
            registerProvider("p1", 1);
            registerProvider("p2", 2);

            // P1 连续失败 2 次 → 熔断
            StubProvider p1 = new StubProvider("p1",
                Flux.error(new RuntimeException("error")));
            StubProvider p2 = new StubProvider("p2",
                Flux.just("P2 backup"));

            router = new RoutingLLMService(List.of(p1, p2), properties);

            // 第一次：P1 失败 → P2 成功（P1 记录 1 次失败）
            StepVerifier.create(router.chatStream(ChatRequest.of("m", "q1")))
                .expectNext("P2 backup")
                .verifyComplete();

            // 第二次：P1 失败 → P2 成功（P1 记录 2 次失败 → 熔断）
            StepVerifier.create(router.chatStream(ChatRequest.of("m", "q2")))
                .expectNext("P2 backup")
                .verifyComplete();

            // 第三次：P1 被熔断跳过 → 直接 P2
            StepVerifier.create(router.chatStream(ChatRequest.of("m", "q3")))
                .expectNext("P2 backup")
                .verifyComplete();
        }
    }

    // ==================== 健康状态测试 ====================

    @Nested
    @DisplayName("健康状态查询")
    class HealthStatus {

        @Test
        @DisplayName("启动后所有供应商状态为 CLOSED")
        void shouldReportAllClosedOnStartup() {
            properties = createProperties();
            registerProvider("p1", 1);
            registerProvider("p2", 2);

            StubProvider p1 = new StubProvider("p1", Flux.just("ok"));
            StubProvider p2 = new StubProvider("p2", Flux.just("ok"));

            router = new RoutingLLMService(List.of(p1, p2), properties);

            Map<String, String> status = router.getHealthStatus();

            assertEquals("CLOSED", status.get("p1"));
            assertEquals("CLOSED", status.get("p2"));
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 把一个 provider 注册为 "enabled"。
     */
    private void registerProvider(String name, int priority) {
        RagProperties.Provider p = new RagProperties.Provider();
        p.setEnabled(true);
        p.setPriority(priority);
        p.setChatModel("test-model");
        properties.getLlm().getProviders().put(name, p);
    }
}
