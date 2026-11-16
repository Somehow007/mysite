package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话。
 * 归属：user_id 非空 → 只认账号（visitorId 失效）；user_id 为空 → 存量会话按 visitor_id 兜底，
 * 续聊时由 ConversationManager 认领（lazy claim）。判定逻辑见 ConversationManager.isOwner。
 */
@Data
@TableName("t_conversation")
public class ConversationDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 登录用户 ID：归属主 key，非空后 visitor_id 不再作为凭证 */
    private Long userId;
    /** 会话归属兜底标识（前端 localStorage UUID），仅未认领会话按此归属 */
    private String visitorId;
    private String title;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
