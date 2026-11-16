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
@TableName("t_collection")
public class CollectionDO extends BaseDO {

    private Long id;
    private String title;
    private String description;
    private String coverImage;
    private Long authorId;
    private Integer articleCount;
    /** 计划总篇数（连载预告，null=未设计划） */
    private Integer plannedCount;
    /** 连载状态：0-连载中 1-已完结（纯手动标记，无 DB 约束） */
    private Integer status;
    /** 可见性：0-公开 1-私有（仅作者和管理员可见） */
    private Integer visibility;
    private Integer sortOrder;
}
