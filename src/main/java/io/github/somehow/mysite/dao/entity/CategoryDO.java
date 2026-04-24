package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_category")
public class CategoryDO extends BaseDO {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Integer sortOrder;
    private Long parentId;
    private Integer level;
    private String path;
    private Integer status;
    private String icon;
    private String color;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
}
