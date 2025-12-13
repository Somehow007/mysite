package io.github.somehow.mysite.dto.resp.user;

import lombok.Data;

/**
 * 用户搜索响应实体
 */
@Data
public class UserSearchRespDTO {

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
     * 粉丝人数
     */
    private Integer followerCount;
}