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
@TableName("t_comment_like")
public class CommentLikeDO extends BaseDO {

    private Long id;
    private Long commentId;
    private Long userId;
    private String ipAddress;
}
