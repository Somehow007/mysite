package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价卡新建/修订请求。PUT 语义=版本化修订：闭合旧版 + 按本请求开新版。
 * peakWindows/tiers 为 JSON 数组文本（可为空）。
 */
@Data
public class LlmModelPriceUpsertRequest {

    private String provider;
    private String model;
    private String displayName;
    private String currency;
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
    /** 新版本生效起点；空 = 当前时间。必须晚于被修订版本的 effective_from。
     *  语义为服务器本地墙钟（与 DB TIMESTAMP / LocalDateTime.now() 一致），无时区；
     *  前端须发 yyyy-MM-ddTHH:mm[:ss] 本地时间串，勿转 UTC ISO。 */
    private LocalDateTime effectiveFrom;
    private String note;
}
