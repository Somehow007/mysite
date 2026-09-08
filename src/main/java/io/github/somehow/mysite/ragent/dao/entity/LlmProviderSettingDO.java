package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_llm_provider_setting")
public class LlmProviderSettingDO {

    @TableId(type = IdType.INPUT)
    private String name;
    private Boolean enabled;
    private Integer priority;
    private String baseUrl;
    private String chatModel;
    private String embeddingModel;
    private String rerankModel;
    private String apiKey;
    private LocalDateTime updateTime;
}
