package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@TableName(value = "t_user_follow")
public class UserFollowDO extends BaseDO {

    private Long id;
    private Long followerId;
    private Long followeeId;
}
