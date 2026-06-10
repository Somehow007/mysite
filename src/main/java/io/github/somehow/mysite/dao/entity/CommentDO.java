package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
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
@TableName("t_comment")
public class CommentDO extends BaseDO {

    private Long id;
    private Long articleId;
    private Long parentId;
    private Long rootId;
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
}
