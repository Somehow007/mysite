package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息。
 * sources 字段存 JSONB，包含引用来源的文章标题和 chunk 片段。
 */
@Data
@TableName("t_conversation_message")
public class ConversationMessageDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    /** 角色：USER / ASSISTANT */
    private String role;
    private String content;
    /** 引用来源，JSONB 格式：[{"docTitle":"...","chunkContent":"...","score":0.92}] */
    private String sources;
    private Integer tokenCount;
    private LocalDateTime createTime;
}
