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
    private Integer articleCount;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;

    @Schema(description = "合集中所有文章的浏览量总和")
    private Long totalViewCount;
}
