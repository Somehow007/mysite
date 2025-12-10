package io.github.somehow.mysite.dto.resp.user;

import lombok.Data;

/**
 * todo: 加一个 ta 的作品
 * 分页获取粉丝 ｜ 关注的人 信息
 */
@Data
public class UserPageQueryFollowRespDTO {

    /**
     * id
     */
    private Long id;

    /**
     * 昵称
     */
    private String username;

    /**
     * 性别 0: 男性 1: 女性 2: 保密
     */
    private Integer sex;

    /**
     * 关注人数
     */
    private Integer followingCount;

    /**
     * 粉丝人数
     */
    private Integer followerCount;
}
