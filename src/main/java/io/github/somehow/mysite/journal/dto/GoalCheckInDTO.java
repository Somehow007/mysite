package io.github.somehow.mysite.journal.dto;

import lombok.Data;

@Data
public class GoalCheckInDTO {

    private String id;
    private String goalId;
    private String date;
    private Integer quantity;
    private Integer durationMin;
    private String note;
    private Long createdAt;
    private Long updatedAt;
}
