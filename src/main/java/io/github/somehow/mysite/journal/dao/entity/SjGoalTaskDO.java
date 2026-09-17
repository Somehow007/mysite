package io.github.somehow.mysite.journal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 清单型子任务（sj_goal_task）。
 */
@Data
@TableName("sj_goal_task")
public class SjGoalTaskDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String goalId;

    private String title;

    /** YYYY-MM-DD，可空 */
    private String dueDate;

    /** 该周周一 YYYY-MM-DD，可空 */
    private String weekStart;

    private Boolean done;

    private Long doneAt;

    private Integer durationMin;

    private String reflection;

    private String learningClientId;

    private Integer sortOrder;

    private Long createdAt;

    private Long updatedAt;
}
