package io.github.somehow.mysite.dto.resp.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class UserPageQueryFollowRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private Integer sex;

    private Integer followingCount;

    private Integer followerCount;
}
