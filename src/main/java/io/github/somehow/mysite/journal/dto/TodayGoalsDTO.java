package io.github.somehow.mysite.journal.dto;

import lombok.Data;

import java.util.List;

@Data
public class TodayGoalsDTO {

    private String date;
    private List<TodayGoalItemDTO> items;
}
