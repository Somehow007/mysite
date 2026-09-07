package io.github.somehow.mysite.ragent.usage;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.LlmUsageDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmUsageMapper;
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
 */
@Slf4j
@Component
public class LlmUsageRecorder {

    private static volatile LlmUsageRecorder instance;

    private final LlmUsageMapper mapper;
    private final Executor executor;
    private final RagProperties properties;

    public LlmUsageRecorder(LlmUsageMapper mapper,
                            @Qualifier("ragAsyncExecutor") Executor executor,
                            RagProperties properties) {
        this.mapper = mapper;
        this.executor = executor;
        this.properties = properties;
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
        RagProperties.PricingProperties pricing = properties.getLlm().getPricing();
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
        row.setTokenSource(u.fromApi() ? "API" : "ESTIMATED");
        row.setCost(calculateCost(model, u, pricing));
        row.setCurrency(pricing.getCurrency() != null ? pricing.getCurrency() : "CNY");
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

    static BigDecimal calculateCost(String model, TokenUsage usage, RagProperties.PricingProperties pricing) {
        if (pricing == null || model == null || usage == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        RagProperties.ModelPrice price = pricing.getModels().get(model);
        if (price == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal million = new BigDecimal("1000000");
        BigDecimal input = nvl(price.getInput())
                .multiply(BigDecimal.valueOf(usage.promptTokens()))
                .divide(million, 8, RoundingMode.HALF_UP);
        BigDecimal output = nvl(price.getOutput())
                .multiply(BigDecimal.valueOf(usage.completionTokens()))
                .divide(million, 8, RoundingMode.HALF_UP);
        return input.add(output).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
