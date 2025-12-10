package io.github.somehow.mysite.dto.resp.user;

import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import lombok.Data;

import java.util.List;

/**
 * 根据id获取用户信息返回实体
 */
@Data
public class UserSelectRespDTO {

    /**
     * id
     */
    private Long id;

    /**
     * 昵称
     */
    private String username;

    /**
     * 姓名 必填
     */
    private String realName;

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

    /**
     * 收藏的文章
     */
    private List<ArticlePageQueryRespDTO> favorites;

    /**
     * 浏览历史
     */
    private List<ArticlePageQueryRespDTO> histories;

}
