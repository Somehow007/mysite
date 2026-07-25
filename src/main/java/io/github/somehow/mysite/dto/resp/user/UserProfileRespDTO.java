package io.github.somehow.mysite.dto.resp.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户公开个人主页响应 DTO。
 * 包含基本信息 + 统计数据（文章数、合集数等）。
 */
@Data
public class UserProfileRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String realName;
    private String avatar;
    private String bio;
    private String location;
    private String website;
    private String role;
    private Integer followingCount;
    private Integer followerCount;
    private LocalDateTime createTime;
    private Long articleCount;
    private Long collectionCount;
    private Long likeCount;
    private Long favoriteCount;
}
