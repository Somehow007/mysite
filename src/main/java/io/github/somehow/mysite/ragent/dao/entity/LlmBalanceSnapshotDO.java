package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 官方账单探针快照：每次 /billing 拉取存一份，供余额差值反推周期实花。 */
@Data
@TableName("t_llm_balance_snapshot")
public class LlmBalanceSnapshotDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String provider;
    private Boolean ok;
    private String payload;
    private Integer latencyMs;
    private LocalDateTime createTime;
}
