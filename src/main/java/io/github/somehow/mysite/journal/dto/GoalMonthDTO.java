package io.github.somehow.mysite.journal.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GoalMonthDTO {

    private String period;
    private Integer monthPercent;
    private Integer reachedCount;
    private Integer goalCount;
    private List<GoalSummaryDTO> goals;
    private Map<String, DayMarkDTO> dayMarks;
}
