package io.github.somehow.mysite.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleFavoriteRespDTO {

    private Boolean favorited;
    private Integer favoriteCount;
}