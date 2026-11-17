package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 消费看板汇总。金额一律按币种分组（byCurrency / 各切片的 currency 字段），
 * 不同币种的 cost 不可相加；totalCalls/successRate 与币种无关。
 */
@Data
public class LlmUsageSummaryDTO {

    private long totalCalls;
    private double successRate;
    private List<CurrencySlice> byCurrency = new ArrayList<>();
    private List<DailyPoint> series = new ArrayList<>();
    private List<NamedSlice> byModel = new ArrayList<>();
    private List<NamedSlice> byCallType = new ArrayList<>();
    private List<UserSlice> byUser = new ArrayList<>();

    @Data
    public static class CurrencySlice {
        private String currency;
        private BigDecimal totalCost;
        private long totalTokens;
        private long totalCalls;
        private long successCalls;
    }

    @Data
    public static class DailyPoint {
        private String day;
        private String currency;
        private BigDecimal cost;
        private long tokens;
        private long calls;
    }

    @Data
    public static class NamedSlice {
        private String name;
        private String provider;
        private String currency;
        private BigDecimal cost;
        private long tokens;
        private long calls;
    }

    @Data
    public static class UserSlice {
        private String actor;
        private String userId;
        private String visitorId;
        private String currency;
        private BigDecimal cost;
        private long tokens;
        private long calls;
    }
}
