package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意图定义实体（t_rag_intent）。
 * <p>
 * 博客规模的扁平意图列表 —— 模式分类，不做领域树。
 * 类型：KB_META（目录/统计）/ KB_RETRIEVAL（内容检索）/ CHAT（闲聊）。
 * 检索范围由用户勾选的知识库决定，不由 kbId 自动选库。
 */
@Data
@TableName("t_rag_intent")
public class IntentDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;                 // 意图名称，如 "知识库统计与概览"
    private String type;                 // KB_META / KB_RETRIEVAL / CHAT
    private Long kbId;                   // 历史字段，管道不再用它选库
    private String keywords;             // 触发关键词 JSON 数组
    private String description;          // 意图描述，给 LLM 分类用
    private Integer priority;            // 优先级，数值越大越优先
    private Boolean enabled;             // 是否启用
    private String customPromptFragment; // 自定义 Prompt 片段
    private Integer customTopK;          // 专用 topK（null = 使用全局默认）
    private LocalDateTime createTime;
}
