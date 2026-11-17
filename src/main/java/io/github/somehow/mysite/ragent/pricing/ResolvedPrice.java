package io.github.somehow.mysite.ragent.pricing;

import io.github.somehow.mysite.ragent.dao.entity.LlmModelPriceDO;
import io.github.somehow.mysite.ragent.usage.TokenUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一次计价命中的结果：价卡 + 按峰谷/阶梯/缓存折算后的有效单价（每 1M token）。
 */
public record ResolvedPrice(Long priceId, String currency,
                            BigDecimal input, BigDecimal cacheRead,
                            BigDecimal cacheWrite, BigDecimal output,
                            boolean peak, boolean peakAware) {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    /** 兼容旧调用：未声明峰谷语义时 peakAware 取 peak 原值。 */
    public ResolvedPrice(Long priceId, String currency,
                         BigDecimal input, BigDecimal cacheRead,
                         BigDecimal cacheWrite, BigDecimal output,
                         boolean peak) {
        this(priceId, currency, input, cacheRead, cacheWrite, output, peak, peak);
    }

    public static ResolvedPrice of(LlmModelPriceDO card, boolean peak,
                                   BigDecimal input, BigDecimal cacheRead,
                                   BigDecimal cacheWrite, BigDecimal output) {
        boolean peakAware = card != null && card.getPeakWindows() != null
                && !card.getPeakWindows().isBlank();
        return new ResolvedPrice(card.getId(), card.getCurrency(), input, cacheRead, cacheWrite, output, peak, peakAware);
    }

    /**
     * cost = (未命中输入×input + 命中×cacheRead + 写入×cacheWrite + 输出×output) / 1M。
     * cacheRead 是 promptTokens 子集；cacheWrite 为独立维度不从 prompt 中扣减。
     */
    public BigDecimal cost(TokenUsage u) {
        if (u == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        int prompt = u.promptTokens();
        int hit = Math.min(Math.max(0, u.cacheReadTokens()), Math.max(0, prompt));
        int miss = Math.max(0, prompt - hit);
        int write = Math.max(0, u.cacheWriteTokens());
        BigDecimal tokensIn = BigDecimal.valueOf(miss);
        BigDecimal cost = nvl(input).multiply(tokensIn)
                .add(nvl(cacheRead).multiply(BigDecimal.valueOf(hit)))
                .add(nvl(cacheWrite).multiply(BigDecimal.valueOf(write)))
                .add(nvl(output).multiply(BigDecimal.valueOf(u.completionTokens())))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        return cost.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
