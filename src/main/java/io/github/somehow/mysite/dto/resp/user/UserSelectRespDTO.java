package io.github.somehow.mysite.dto.resp.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import lombok.Data;

import java.util.List;

@Data
public class UserSelectRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String realName;

    private Integer sex;

    private Integer followingCount;

    private Integer followerCount;

    private List<ArticlePageQueryRespDTO> favorites;

    private List<ArticlePageQueryRespDTO> histories;

}
