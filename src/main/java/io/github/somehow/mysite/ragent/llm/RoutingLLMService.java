package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模型路由器 —— Ragent 整体设计的核心
 *
 * 职责：
 *  1. 按优先级排序所有已启用的供应商
 *  2. 遍历尝试，跳过被熔断的
 *  3. 调用成功 -> 记录成功 + 返回结果
 *  4. 调用失败 -> 记录失败 + 尝试下一个
 *  5. 全部失败 -> 抛出异常
 *
 * 这个类的本质是“责任链 + 策略模式”：每个 Provider 是一个策略，
 * 路由器按优先级串联成降级链。
 *
 * 两个关键设计点：
 *  1. 注入 List<LLMProvider> 而不是 List<LLMService> —— 本类自身也是
 *     LLMService，注入后者会把自己装进 List，形成循环依赖
 *  2. 流式降级边界 —— 只在“尚未输出任何 token”时才允许降级；
 *     一档已经吐过 token，失败必须直接报错给前端，
 *     否则用户会看到两段拼起来的回答
 */
@Slf4j
@Service
public class RoutingLLMService implements LLMService{

    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final List<LLMProvider> sortedProviders;    // 启动时排序，运行时不变

    public RoutingLLMService(List<LLMProvider> allowProviders, RagProperties properties) {
        // Spring 注入所有 LLMProvider 实现（不含本类自身）
        this.sortedProviders = allowProviders.stream()
                .filter(p -> properties.isProviderEnabled(p.getName()))
                .sorted(Comparator.comparingInt(p -> properties.getProviderPriority(p.getName())))
                .toList();
        // 为每个启用的供应商创建独立断路器
        int threshold = properties.getCircuitBreaker().getFailureThreshold();
        long cooldown = properties.getCircuitBreaker().getCooldownSeconds();
        sortedProviders.forEach(p ->
                breakers.put(p.getName(), new CircuitBreaker(p.getName(), threshold, cooldown)));
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        log.info("[routing] attempting LLM with {} providers: {}",
            sortedProviders.size(),
            sortedProviders.stream().map(LLMProvider::getName).toList());
        return attempt(sortedProviders.iterator(), request);
    }

    @Override
    public String chat(ChatRequest request) {
        return chatStream(request)
                .collectList()
                .map(tokens -> String.join("", tokens))
                .block(Duration.ofSeconds(120));
    }

    /**
     * 递归尝试供应商链。
     *
     * 成功/失败的记录时机
     *  - recordSuccess：流完整结束（doOnComplete）才算成功。
     *    不能在“发出请求”时就算成功 —— 连接成功但流中途挂掉也应记失败。
     *  - recordFailure：onErrorResume 时记录。
     *
     *  降级边界：
     *      emitted 标记当前供应商是否已经输出过 token。
     *      - 未输出就失败 -> 用户还什么都没看到，可以安全降级到下一个供应商
     *      - 已输出后失败 -> 不能降级（否则前端收到两段拼接的回答），直接报错
     */
    private Flux<String> attempt(Iterator<LLMProvider> it, ChatRequest request) {
        if (!it.hasNext()) {
            return Flux.error(new RuntimeException("All LLM providers failed"));
        }
        LLMProvider provider = it.next();
        CircuitBreaker cb = breakers.get(provider.getName());
        if (cb != null && !cb.allowRequest()) {
            log.info("[routing] skipping {} (breaker {})", provider.getName(), cb.getState());
            return attempt(it, request);    // 熔断中，跳过
        }

        log.info("[routing] trying provider: {} model={}", provider.getName(),
            request.getModel() != null ? request.getModel() : "(default)");
        long t0 = System.currentTimeMillis();
        AtomicBoolean emitted = new AtomicBoolean(false);
        return provider.chatStream(request)
                .doOnNext(token -> emitted.set(true))
                .doOnComplete(() -> {
                    cb.recordSuccess();
                    log.info("[routing] {} completed successfully ({}ms)", provider.getName(),
                        System.currentTimeMillis() - t0);
                })
                .onErrorResume(e -> {
                    cb.recordFailure();
                    log.warn("[routing] {} failed after {}ms: {}",
                        provider.getName(), System.currentTimeMillis() - t0, e.getMessage());
                    if (emitted.get()) {
                        // 已经吐过 token：不能降级，直接失败
                        return Flux.error(e);
                    }
                    // 尚未输出：降级到下一个供应商
                    return attempt(it, request);
                });
    }

    /**
     * 获取各供应商的健康状态（用于 /actuator 健康检查或 Dashboard）
     */
    public Map<String, String> getHealthStatus() {
        Map<String, String> status = new LinkedHashMap<>();
        for (LLMProvider provider : sortedProviders) {
            status.put(provider.getName(), breakers.get(provider.getName()).getState().name());
        }
        return status;
    }
}
