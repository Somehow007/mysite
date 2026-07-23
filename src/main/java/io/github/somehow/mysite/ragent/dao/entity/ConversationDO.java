package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话。
 * 支持匿名（visitorId）和登录用户（userId）两种归属方式。
 */
@Data
@TableName("t_conversation")
public class ConversationDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 登录用户 ID，匿名聊天时为 null */
    private Long userId;
    /** 匿名访客标识（前端 localStorage UUID），用于非登录用户的会话归属 */
    private String visitorId;
    private String title;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
