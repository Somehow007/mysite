package io.github.somehow.mysite.dto.resp.category;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class CategoryRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Integer sortOrder;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    private Integer level;
    private String path;
    private Integer status;
    private String icon;
    private String color;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private Long articleCount;
    private List<CategoryRespDTO> children;
}
