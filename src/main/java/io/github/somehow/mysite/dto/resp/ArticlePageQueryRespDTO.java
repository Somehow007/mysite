package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ArticlePageQueryRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String summary;
    private String coverImage;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer readingTime;
    private Integer published;
    private String authorName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String categoryName;
    private String categorySlug;
    private java.util.Date createTime;
    private java.util.Date updateTime;
    private Boolean isFavorited;

    // 合集相关字段
    @JsonSerialize(using = ToStringSerializer.class)
    private Long collectionId;
    private String collectionTitle;
    private Integer collectionSortOrder;
}
