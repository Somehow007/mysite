package io.github.somehow.mysite.journal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 手帐月计划大任务（sj_goal）。主键为前端 nanoid。
 */
@Data
@TableName("sj_goal")
public class SjGoalDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;

    /** YYYY-MM */
    private String period;

    private String title;

    /** COUNT / CHECKLIST */
    private String type;

    /** COUNT 目标值 */
    private Integer targetValue;

    /** COUNT 单位 */
    private String unit;

    private String color;

    private String note;

    private Integer sortOrder;

    /** ACTIVE / ARCHIVED */
    private String status;

    private Long createdAt;

    private Long updatedAt;
}
