package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ArticleSelectRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String content;
    private String summary;
    private String coverImage;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String authorName;
    private Integer viewCount;
    private Integer favoriteCount;
    private Date updateTime;
    private List<TagInfo> tags;

    @Data
    public static class TagInfo {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String name;
        private String slug;
    }
}
