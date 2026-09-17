package io.github.somehow.mysite.journal.dto;

import lombok.Data;

@Data
public class GoalTaskDTO {

    private String id;
    private String goalId;
    private String title;
    private String dueDate;
    private String weekStart;
    private Boolean done;
    private Long doneAt;
    private Integer durationMin;
    private String reflection;
    private Integer sortOrder;
    private Long createdAt;
    private Long updatedAt;
}
