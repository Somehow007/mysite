package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ArticlePageQueryRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 摘要/简介（可选，用于列表页展示）
     */
    private String summary;

    /**
     * 阅读量
     */
    private Integer viewCount;

    /**
     * 收藏量
     */
    private Integer favoriteCount;

    /**
     * 作者名称
     */
    private String authorName;
}
