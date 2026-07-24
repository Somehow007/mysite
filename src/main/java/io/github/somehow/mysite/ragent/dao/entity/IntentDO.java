package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意图定义实体（t_rag_intent）。
 * <p>
 * 博客规模的扁平意图列表 —— 不做 Ragent 的树形多级结构。
 * 每个意图绑定一个知识库（KB_RETRIEVAL 类型）或不绑定（CHAT 类型），
 * 带自定义 Prompt 片段和检索参数覆盖。
 */
@Data
@TableName("t_rag_intent")
public class IntentDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;                 // 意图名称，如 "技术博客检索"
    private String type;                 // KB_RETRIEVAL / CHAT
    private Long kbId;                   // 绑定的知识库（CHAT 类型为 null）
    private String keywords;             // 触发关键词 JSON 数组
    private String description;          // 意图描述，给 LLM 分类用
    private Integer priority;            // 优先级，数值越大越优先
    private Boolean enabled;             // 是否启用
    private String customPromptFragment; // 自定义 Prompt 片段
    private Integer customTopK;          // 专用 topK（null = 使用全局默认）
    private LocalDateTime createTime;
}
