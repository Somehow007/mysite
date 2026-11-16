package io.github.somehow.mysite.dto.resp.collection;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
public class CollectionPageQueryRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String description;
    private String coverImage;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String authorName;

    @Schema(description = "当前访问者可见的文章数量（动态统计，私有文章对非作者不计入）")
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

    @Schema(description = "合集中所有文章的浏览量总和")
    private Long totalViewCount;
}
