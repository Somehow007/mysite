package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话。
 * 当前所有会话均以 visitorId（浏览器 UUID）归属；userId 列预留，暂未启用。
 */
@Data
@TableName("t_conversation")
public class ConversationDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 登录用户 ID，预留字段（当前不写入） */
    private Long userId;
    /** 会话归属标识（前端 localStorage UUID），会话历史按此归属 */
    private String visitorId;
    private String title;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
