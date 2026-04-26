package io.github.somehow.mysite.dto.resp.admin;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class AdminUserRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String realName;

    private String email;

    private String phoneNumber;

    private Integer sex;

    private String role;

    private Integer status;

    private Integer followingCount;

    private Integer followerCount;

    private Date createTime;

    private Date updateTime;
}
