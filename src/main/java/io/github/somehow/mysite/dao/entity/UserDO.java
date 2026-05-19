package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import io.github.somehow.mysite.commons.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_user")
public class UserDO extends BaseDO {

    private Long id;

    private String username;

    private String password;

    private String realName;

    private Integer sex;

    private String email;

    private String phoneNumber;

    @Builder.Default
    private UserRole role = UserRole.USER;

    @Builder.Default
    private Integer status = 1;

    private Integer followingCount;

    private Integer followerCount;

    private String avatar;
}
