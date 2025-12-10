package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_user")
public class UserDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 昵称
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 姓名 必填
     */
    private String realName;

    /**
     * 性别 0: 男性 1: 女性 2: 保密
     */
    private Integer sex;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号 必填
     */
    private String phoneNumber;

    /**
     * 关注人数
     */
    private Integer followingCount;

    /**
     * 粉丝人数
     */
    private Integer followerCount;
}
