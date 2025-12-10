package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.Builder;
import lombok.Data;

/**
 * 用户关注数据库信息
 */
@Data
@Builder
@TableName(value = "t_user_follow")
public class UserFollowDO extends BaseDO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 关注者Id
     */
    private Long followerId;

    /**
     * 被关注者Id
     */
    private Long followeeId;

}
