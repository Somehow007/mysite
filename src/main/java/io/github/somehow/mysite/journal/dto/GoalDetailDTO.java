package io.github.somehow.mysite.journal.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class GoalDetailDTO extends GoalSummaryDTO {

    private List<GoalTaskDTO> tasks;
    private List<GoalCheckInDTO> checkins;
    private List<WeekProgressDTO> weeks;
}
