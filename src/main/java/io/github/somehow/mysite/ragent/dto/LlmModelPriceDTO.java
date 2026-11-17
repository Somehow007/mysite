package io.github.somehow.mysite.ragent.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 价卡视图（版本化）。peakWindows/tiers 为 JSON 原文，前端按文本编辑。 */
@Data
public class LlmModelPriceDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String provider;
    private String model;
    private String displayName;
    private String currency;
    private String unit;
    private BigDecimal inputPrice;
    private BigDecimal cacheReadPrice;
    private BigDecimal cacheReadFactor;
    private BigDecimal cacheWritePrice;
    private BigDecimal cacheWriteFactor;
    private BigDecimal outputPrice;
    private String peakWindows;
    private BigDecimal offPeakFactor;
    private Boolean excludeHolidays;
    private String tiers;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String source;
    private String note;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 派生：当前是否为生效版本（effective_from <= now < effective_to）。 */
    private Boolean current;
}
