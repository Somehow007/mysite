package io.github.somehow.mysite.ragent.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LlmUsageLogDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String traceId;
    private String callType;
    private String provider;
    private String model;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String username;
    private String visitorId;
    private String userRole;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String tokenSource;
    private BigDecimal cost;
    private String currency;
    private Integer latencyMs;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime createTime;
}
