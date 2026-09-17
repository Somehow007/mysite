package io.github.somehow.mysite.journal.dto;

import lombok.Data;

import java.util.List;

@Data
public class TodayGoalItemDTO {

    private String goalId;
    private String title;
    private String type;
    private String color;
    private Integer percent;
    private Boolean reached;
    private String unit;
    private Integer targetValue;
    private Integer actualValue;
    private Integer remaining;
    private Integer suggestedToday;
    private GoalCheckInDTO todayCheckin;
    private List<GoalTaskDTO> todayTasks;
}
