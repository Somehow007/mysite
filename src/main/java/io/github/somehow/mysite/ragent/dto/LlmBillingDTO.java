package io.github.somehow.mysite.ragent.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** GET /v1/admin/ai/billing 响应：逐 provider 的官方账单快照 + 差值对账。 */
@Data
public class LlmBillingDTO {

    private LocalDateTime probedAt;
    private List<ProviderBilling> providers;

    @Data
    public static class ProviderBilling {
        private String provider;
        private Boolean ok;
        private Long latencyMs;
        private String message;
        /** 规范化数据：balance → balances[]，quota → dailySpend/monthlySpend/models 等 */
        private Map<String, Object> data;
        /** 与上一份快照的差值（仅 balance 类有意义：total 余额下降 = 实花） */
        private Delta delta;
    }

    @Data
    public static class Delta {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long fromSnapshotId;
        private LocalDateTime fromTime;
        /** 币种 → 区间实花（官方口径，字符串避免精度丢失） */
        private Map<String, String> periodSpend;
        private Map<String, String> fromTotal;
        private Map<String, String> toTotal;
    }
}
