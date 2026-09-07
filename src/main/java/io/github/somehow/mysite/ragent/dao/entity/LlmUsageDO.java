package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_llm_usage")
public class LlmUsageDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String traceId;
    private String callType;
    private String provider;
    private String model;
    private Long userId;
    private String username;
    private String visitorId;
    private String userRole;
    private Long conversationId;
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
