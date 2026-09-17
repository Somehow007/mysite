package io.github.somehow.mysite.journal.dto;

import lombok.Data;

@Data
public class WeekProgressDTO {

    private String weekStart;
    private String weekEnd;
    private Integer actual;
    private Integer planned;
}
