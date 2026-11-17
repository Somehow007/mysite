package io.github.somehow.mysite.ragent.usage;

import io.github.somehow.mysite.ragent.dao.entity.LlmUsageDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmUsageMapper;
import io.github.somehow.mysite.ragent.pricing.ModelPriceResolver;
import io.github.somehow.mysite.ragent.pricing.ResolvedPrice;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * 异步写入用量。失败只打日志，不影响主路径。
 * 计价走 t_llm_model_price 价卡（峰谷/阶梯/缓存维度），写入时算死，改价不影响历史。
 */
@Slf4j
@Component
public class LlmUsageRecorder {

    private static volatile LlmUsageRecorder instance;

    private final LlmUsageMapper mapper;
    private final Executor executor;
    private final ModelPriceResolver priceResolver;

    public LlmUsageRecorder(LlmUsageMapper mapper,
                            @Qualifier("ragAsyncExecutor") Executor executor,
                            ModelPriceResolver priceResolver) {
        this.mapper = mapper;
        this.executor = executor;
        this.priceResolver = priceResolver;
    }

    @PostConstruct
    void register() {
        instance = this;
    }

    public static void record(UsageContext.State ctx, String provider, String model,
                              TokenUsage usage, long latencyMs, boolean success, String error) {
        LlmUsageRecorder rec = instance;
        if (rec == null) {
            return;
        }
        rec.enqueue(ctx, provider, model, usage, latencyMs, success, error);
    }

    private void enqueue(UsageContext.State ctx, String provider, String model,
                         TokenUsage usage, long latencyMs, boolean success, String error) {
        try {
            LlmUsageDO row = buildRow(ctx, provider, model, usage, latencyMs, success, error);
            executor.execute(() -> {
                try {
                    mapper.insert(row);
                } catch (Exception e) {
                    log.warn("[usage] insert failed: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[usage] record skipped: {}", e.getMessage());
        }
    }

    private LlmUsageDO buildRow(UsageContext.State ctx, String provider, String model,
                                TokenUsage usage, long latencyMs, boolean success, String error) {
        LlmUsageDO row = new LlmUsageDO();
        row.setTraceId(ctx.getTraceId());
        row.setCallType(ctx.getCallType() != null ? ctx.getCallType() : "CHAT");
        row.setProvider(provider != null ? provider : "unknown");
        row.setModel(model != null ? model : "unknown");
        row.setUserId(ctx.getUserId());
        row.setUsername(ctx.getUsername());
        row.setVisitorId(ctx.getVisitorId());
        row.setUserRole(ctx.getUserRole());
        row.setConversationId(ctx.getConversationId());
        row.setDocumentId(ctx.getDocumentId());
        TokenUsage u = usage != null ? usage : TokenUsage.estimated(0, 0);
        row.setPromptTokens(u.promptTokens());
        row.setCompletionTokens(u.completionTokens());
        row.setTotalTokens(u.totalTokens());
        row.setCacheReadTokens(u.cacheReadTokens());
        row.setCacheWriteTokens(u.cacheWriteTokens());
        row.setTokenSource(u.fromApi() ? "API" : "ESTIMATED");
        // 计价时点取请求开始时刻近似（记录时刻 - 调用耗时）：长流跨峰谷边界时按请求侧判定更贴近官方口径
        LocalDateTime at = LocalDateTime.now()
                .minusNanos(Math.max(0, latencyMs) * 1_000_000L);
        ResolvedPrice price = resolvePrice(row.getProvider(), row.getModel(), at, u.promptTokens());
        if (price != null) {
            row.setCost(price.cost(u));
            row.setCurrency(price.currency());
            row.setPriceId(price.priceId());
            // peak 只在卡声明了峰谷窗时才有意义：无峰谷概念的模型落 NULL，前端不显示徽标
            row.setPeak(price.peakAware() ? price.peak() : null);
        } else {
            // 未标价模型：cost 恒 0，currency 仅为列占位（不产生任何币种金额）
            row.setCost(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
            row.setCurrency("CNY");
        }
        row.setLatencyMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, latencyMs)));
        row.setSuccess(success);
        if (error != null && error.length() > 1000) {
            row.setErrorMessage(error.substring(0, 1000));
        } else {
            row.setErrorMessage(error);
        }
        row.setCreateTime(LocalDateTime.now());
        return row;
    }

    /**
     * 价卡解析失败（tiers/peak_windows 畸形 JSON 等）降级为未命中，
     * 保证 cost=0 的占位记录不丢，usage 主链路不受计价侧数据问题影响。
     */
    private ResolvedPrice resolvePrice(String provider, String model, LocalDateTime at, int promptTokens) {
        try {
            return priceResolver.resolve(provider, model, at, promptTokens);
        } catch (Exception e) {
            log.warn("[usage] price resolve failed for {}/{}: {}", provider, model, e.getMessage());
            return null;
        }
    }
}

