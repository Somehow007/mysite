package io.github.somehow.mysite.journal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 数量型每日打卡（sj_goal_checkin），同一目标同一天至多一条。
 */
@Data
@TableName("sj_goal_checkin")
public class SjGoalCheckinDO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String goalId;

    /** YYYY-MM-DD */
    private String date;

    private Integer quantity;

    private Integer durationMin;

    private String note;

    private String learningClientId;

    private Long createdAt;

    private Long updatedAt;
}
