package io.github.somehow.mysite.dto.resp;

import lombok.Data;

@Data
public class CommentLikeRespDTO {

    private Boolean liked;
    private Integer likeCount;
}
