package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class CommentTreeRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long rootId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;
    private String email;
    private String avatar;
    private String content;
    private Integer likeCount;
    private Integer replyCount;
    private Integer status;
    private Boolean isLiked;
    private Date createTime;
    private List<CommentTreeRespDTO> replies;
}
