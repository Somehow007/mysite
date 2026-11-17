package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型计价卡（版本化）。peak_windows / tiers 为 JSON TEXT。
 * effective_from <= t < COALESCE(effective_to) 取最新 effective_from 为生效版。
 */
@Data
@TableName("t_llm_model_price")
public class LlmModelPriceDO {

    @TableId(type = IdType.ASSIGN_ID)
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
}
