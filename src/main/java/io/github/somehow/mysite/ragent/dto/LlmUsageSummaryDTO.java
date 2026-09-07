package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class LlmUsageSummaryDTO {

    private BigDecimal totalCost;
    private long totalTokens;
    private long totalCalls;
    private double successRate;
    private String currency;
    private List<DailyPoint> series = new ArrayList<>();
    private List<NamedSlice> byModel = new ArrayList<>();
    private List<NamedSlice> byCallType = new ArrayList<>();
    private List<UserSlice> byUser = new ArrayList<>();

    @Data
    public static class DailyPoint {
        private String day;
        private BigDecimal cost;
        private long tokens;
        private long calls;
    }

    @Data
    public static class NamedSlice {
        private String name;
        private String provider;
        private BigDecimal cost;
        private long tokens;
        private long calls;
    }

    @Data
    public static class UserSlice {
        private String actor;
        private String userId;
        private String visitorId;
        private BigDecimal cost;
        private long tokens;
        private long calls;
    }
}
