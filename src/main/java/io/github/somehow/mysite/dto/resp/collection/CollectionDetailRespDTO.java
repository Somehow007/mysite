package io.github.somehow.mysite.dto.resp.collection;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class CollectionDetailRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String description;
    private String coverImage;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String authorName;
    private Integer articleCount;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
    private List<CollectionArticleItemDTO> articles;

    @Data
    public static class CollectionArticleItemDTO {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String title;
        private String summary;
        private String coverImage;
        private String authorName;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long authorId;
        private Integer viewCount;
        private Integer favoriteCount;
        private Integer readingTime;
        private Integer sortOrder;
        private Date createTime;
    }
}
