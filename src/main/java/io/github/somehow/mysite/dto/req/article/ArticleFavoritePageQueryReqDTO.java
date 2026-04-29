package io.github.somehow.mysite.dto.req.article;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "分页获取用户收藏文章请求体")
public class ArticleFavoritePageQueryReqDTO extends Page {

    @Schema(hidden = true)
    private String userId;

    @Schema(description = "搜索关键词")
    private String keyword;
}
