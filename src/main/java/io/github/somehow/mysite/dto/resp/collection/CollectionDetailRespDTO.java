package io.github.somehow.mysite.dto.resp.collection;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "计划总篇数（连载预告，null=未设计划）")
    private Integer plannedCount;

    @Schema(description = "连载状态：0-连载中 1-已完结")
    private Integer status;

    @Schema(description = "可见性：0-公开 1-私有")
    private Integer visibility;

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
