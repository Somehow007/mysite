package io.github.somehow.mysite.journal.dto;

import lombok.Data;

/**
 * 月计划列表中的单项目标摘要（含实时进度）。
 */
@Data
public class GoalSummaryDTO {

    private String id;
    private String period;
    private String title;
    private String type;
    private Integer targetValue;
    private String unit;
    private String color;
    private String note;
    private Integer sortOrder;
    private String status;

    /** 0–100，读时计算 */
    private Integer percent;
    private Boolean reached;

    /** COUNT：累计数量；CHECKLIST：已完成子任务数 */
    private Integer actualValue;
    /** CHECKLIST：子任务总数；COUNT：等于 targetValue */
    private Integer totalValue;

    private Integer plannedDaily;
    private Integer plannedWeekly;
    private Integer suggestedDaily;
    private Integer suggestedWeekly;
    private Integer remaining;
    private Integer remainingDays;

    private Long createdAt;
    private Long updatedAt;
}
