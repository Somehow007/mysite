package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

@Data
public class CommentAdminRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;

    private String articleTitle;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;
    private String email;
    private String avatar;
    private String content;
    private String ipAddress;
    private String userAgent;
    private Integer likeCount;
    private Integer replyCount;
    private Integer status;
    private Date createTime;
}
