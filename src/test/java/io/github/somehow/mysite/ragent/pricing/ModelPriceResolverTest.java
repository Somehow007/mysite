package io.github.somehow.mysite.ragent.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.dao.entity.LlmHolidayDO;
import io.github.somehow.mysite.ragent.dao.entity.LlmModelPriceDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmHolidayMapper;
import io.github.somehow.mysite.ragent.dao.mapper.LlmModelPriceMapper;
import io.github.somehow.mysite.ragent.usage.TokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 计价解析单测：峰谷窗口（UTC+工作日+节假日）、输入长度阶梯、缓存命中价、版本生效期。
 * at 参数一律用「目标 UTC 时刻 → 系统时区 LocalDateTime」构造，测试与机器时区无关。
 */
class ModelPriceResolverTest {

    private static final String DEEPSEEK_WINDOWS =
            "[{\"start\":\"01:00\",\"end\":\"04:00\",\"days\":[1,2,3,4,5]},"
            + "{\"start\":\"06:00\",\"end\":\"10:00\",\"days\":[1,2,3,4,5]}]";

    private static final String QWEN35_TIERS =
            "[{\"max_input_tokens\":131072,\"input_price\":0.8,\"cache_read_price\":0.08,\"output_price\":4.8},"
            + "{\"max_input_tokens\":262144,\"input_price\":2,\"cache_read_price\":0.2,\"output_price\":12},"
            + "{\"max_input_tokens\":1048576,\"input_price\":4,\"cache_read_price\":0.4,\"output_price\":24}]";

    private static long nextId = 1;

    // ── DeepSeek 峰谷 ──

    @Test
    void peakWindowAppliesFullPrice() {
        ModelPriceResolver r = resolver(List.of(deepseekFlash()), List.of());
        // 2026-01-06 周二 02:30 UTC ∈ 01:00-04:00 峰窗
        ResolvedPrice p = r.resolve("deepseek", "deepseek-flash", atUtc(2026, 1, 6, 2, 30), 1000);
        assertNotNull(p);
        assertTrue(p.peak());
        assertEquals(0, new BigDecimal("2").compareTo(p.input()));
        assertEquals(0, new BigDecimal("0.04").compareTo(p.cacheRead()));
        assertEquals(0, new BigDecimal("8").compareTo(p.output()));
        assertEquals("CNY", p.currency());
        assertNotNull(p.priceId());
    }

    @Test
    void offPeakWindowHalvesPrice() {
        ModelPriceResolver r = resolver(List.of(deepseekFlash()), List.of());
        // 同日 05:00 UTC：峰窗之间 → 谷时，全部单价 ×0.5
        ResolvedPrice p = r.resolve("deepseek", "deepseek-flash", atUtc(2026, 1, 6, 5, 0), 1000);
        assertNotNull(p);
        assertFalse(p.peak());
        assertEquals(0, new BigDecimal("1").compareTo(p.input()));
        assertEquals(0, new BigDecimal("0.02").compareTo(p.cacheRead()));
        assertEquals(0, new BigDecimal("4").compareTo(p.output()));
    }

    @Test
    void windowBoundaryIsExclusiveAtEnd() {
        ModelPriceResolver r = resolver(List.of(deepseekFlash()), List.of());
        // 04:00 UTC 正好是窗口 end（不含）→ 谷时；01:00 是 start（含）→ 峰时
        assertFalse(r.resolve("deepseek", "deepseek-flash", atUtc(2026, 1, 6, 4, 0), 1000).peak());
        assertTrue(r.resolve("deepseek", "deepseek-flash", atUtc(2026, 1, 6, 1, 0), 1000).peak());
    }

    @Test
    void weekendInsideWindowHoursIsOffPeak() {
        ModelPriceResolver r = resolver(List.of(deepseekFlash()), List.of());
        // 2026-01-10 周六 02:30 UTC：时刻在窗内但非工作日 → 谷时
        assertFalse(r.resolve("deepseek", "deepseek-flash", atUtc(2026, 1, 10, 2, 30), 1000).peak());
    }

    @Test
    void statutoryHolidayForcesOffPeak() {
        LlmHolidayDO h = new LlmHolidayDO();
        h.setHolidayDate(LocalDate.of(2026, 1, 7)); // 周三，上海日期同日
        ModelPriceResolver r = resolver(List.of(deepseekFlash()), List.of(h));
        // 2026-01-07 02:30 UTC = 上海 10:30，同一天 → 节假日全天谷时
        ResolvedPrice p = r.resolve("deepseek", "deepseek-flash", atUtc(2026, 1, 7, 2, 30), 1000);
        assertNotNull(p);
        assertFalse(p.peak());
        assertEquals(0, new BigDecimal("1").compareTo(p.input()));
    }

    // ── 成本计算：缓存命中 ──

    @Test
    void cacheHitTokensBillAtCacheReadPrice() {
        ModelPriceResolver r = resolver(List.of(deepseekFlash()), List.of());
        TokenUsage u = TokenUsage.api(1000, 500, 1500, 800, 0);
        ResolvedPrice p = r.resolve("deepseek", "deepseek-flash", atUtc(2026, 1, 6, 2, 30), 1000);
        // 峰时：(200 未命中×2 + 800 命中×0.04 + 500 输出×8) / 1M = 0.004432
        assertCost("0.004432", p.cost(u));
        // 无缓存命中同用量 = 0.006，命中确实省钱
        assertCost("0.006", p.cost(TokenUsage.api(1000, 500, 1500)));
    }

    // ── 百炼阶梯价 ──

    @Test
    void tieredPricingSelectsByPromptLength() {
        ModelPriceResolver r = resolver(List.of(qwen35Plus()), List.of());
        LocalDateTime at = atUtc(2026, 3, 2, 12, 0); // 无峰谷配置 → 标准价

        ResolvedPrice small = r.resolve("bailian", "qwen3.5-plus", at, 100_000);
        assertNotNull(small);
        assertEquals(0, new BigDecimal("0.8").compareTo(small.input()));
        assertEquals(0, new BigDecimal("4.8").compareTo(small.output()));
        // (100000×0.8 + 1000×4.8)/1M = 0.0848
        assertCost("0.0848", small.cost(TokenUsage.api(100_000, 1000, 101_000)));

        ResolvedPrice large = r.resolve("bailian", "qwen3.5-plus", at, 200_000);
        assertNotNull(large);
        assertEquals(0, new BigDecimal("2").compareTo(large.input()));
        assertEquals(0, new BigDecimal("12").compareTo(large.output()));
        // (200000×2 + 1000×12)/1M = 0.412
        assertCost("0.412", large.cost(TokenUsage.api(200_000, 1000, 201_000)));
    }

    @Test
    void tierCacheReadPriceOverridesCardLevel() {
        ModelPriceResolver r = resolver(List.of(qwen35Plus()), List.of());
        ResolvedPrice p = r.resolve("bailian", "qwen3.5-plus", atUtc(2026, 3, 2, 12, 0), 100_000);
        // tier 显式 cache_read_price=0.08（卡级也是 0.08，这里验证 tier 字段被读取）
        assertEquals(0, new BigDecimal("0.08").compareTo(p.cacheRead()));
    }

    // ── 版本生效期 ──

    @Test
    void versionedCardPicksEffectiveVersion() {
        LlmModelPriceDO v1 = card("deepseek", "deepseek-flash", "CNY");
        v1.setInputPrice(new BigDecimal("1"));
        v1.setOutputPrice(new BigDecimal("2"));
        v1.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));

        LlmModelPriceDO v2 = card("deepseek", "deepseek-flash", "CNY");
        v2.setInputPrice(new BigDecimal("5"));
        v2.setOutputPrice(new BigDecimal("10"));
        v2.setEffectiveFrom(LocalDateTime.of(2026, 6, 1, 0, 0));

        ModelPriceResolver r = resolver(List.of(v1, v2), List.of());
        // 3 月命中 v1，7 月命中 v2（effective_from 降序取第一条覆盖 at 的）
        assertEquals(0, new BigDecimal("1").compareTo(
                r.resolve("deepseek", "deepseek-flash", atUtc(2026, 3, 1, 12, 0), 100).input()));
        assertEquals(0, new BigDecimal("5").compareTo(
                r.resolve("deepseek", "deepseek-flash", atUtc(2026, 7, 1, 12, 0), 100).input()));
        // 2025 年早于所有版本 → null
        assertNull(r.resolve("deepseek", "deepseek-flash", atUtc(2025, 12, 1, 12, 0), 100));
    }

    // ── 未标价 ──

    @Test
    void unknownModelReturnsNull() {
        ModelPriceResolver r = resolver(List.of(deepseekFlash()), List.of());
        assertNull(r.resolve("deepseek", "nonexistent-model", atUtc(2026, 1, 6, 2, 30), 100));
        assertNull(r.resolve("unknown-provider", "deepseek-flash", atUtc(2026, 1, 6, 2, 30), 100));
    }

    // ── helpers ──

    private static ModelPriceResolver resolver(List<LlmModelPriceDO> cards, List<LlmHolidayDO> holidays) {
        LlmModelPriceMapper pm = mock(LlmModelPriceMapper.class);
        when(pm.selectList(any())).thenReturn(cards);
        LlmHolidayMapper hm = mock(LlmHolidayMapper.class);
        when(hm.selectList(any())).thenReturn(holidays);
        return new ModelPriceResolver(pm, hm, new ObjectMapper());
    }

    private static LlmModelPriceDO deepseekFlash() {
        LlmModelPriceDO d = card("deepseek", "deepseek-flash", "CNY");
        d.setInputPrice(new BigDecimal("2"));
        d.setCacheReadPrice(new BigDecimal("0.04"));
        d.setOutputPrice(new BigDecimal("8"));
        d.setPeakWindows(DEEPSEEK_WINDOWS);
        d.setOffPeakFactor(new BigDecimal("0.5"));
        d.setExcludeHolidays(true);
        d.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        return d;
    }

    private static LlmModelPriceDO qwen35Plus() {
        LlmModelPriceDO d = card("bailian", "qwen3.5-plus", "CNY");
        d.setInputPrice(new BigDecimal("0.8"));
        d.setCacheReadPrice(new BigDecimal("0.08"));
        d.setOutputPrice(new BigDecimal("4.8"));
        d.setTiers(QWEN35_TIERS);
        d.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        return d;
    }

    private static LlmModelPriceDO card(String provider, String model, String currency) {
        LlmModelPriceDO d = new LlmModelPriceDO();
        d.setId(nextId++);
        d.setProvider(provider);
        d.setModel(model);
        d.setCurrency(currency);
        d.setUnit("PER_MILLION_TOKENS");
        return d;
    }

    /** 目标 UTC 时刻 → 系统默认时区的 LocalDateTime（resolver 内部再转回 UTC 判峰谷）。 */
    private static LocalDateTime atUtc(int y, int m, int d, int hh, int mm) {
        return ZonedDateTime.of(y, m, d, hh, mm, 0, 0, ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private static void assertCost(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }
}