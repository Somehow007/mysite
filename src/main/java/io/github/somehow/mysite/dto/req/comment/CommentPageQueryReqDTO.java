package io.github.somehow.mysite.dto.req.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CommentPageQueryReqDTO {

    @Schema(description = "当前页码")
    private Long current = 1L;

    @Schema(description = "每页条数")
    private Long size = 20L;

    @Schema(description = "文章ID")
    private Long articleId;

    @Schema(description = "状态 0:待审核 1:已通过 2:已拒绝")
    private Integer status;

    @Schema(description = "关键词搜索")
    private String keyword;

    @Schema(description = "排序字段: createTime-创建时间, likeCount-点赞数, replyCount-回复数", example = "createTime")
    private String sortField;

    @Schema(description = "排序方向: asc-升序, desc-降序", example = "desc")
    private String sortOrder;
}
