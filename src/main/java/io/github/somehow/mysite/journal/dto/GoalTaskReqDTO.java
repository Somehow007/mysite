package io.github.somehow.mysite.journal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新清单子任务。id 仅创建时必填。
 */
@Data
public class GoalTaskReqDTO {

    @Size(max = 21, message = "子任务 id 过长")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "子任务 id 只能包含字母、数字、下划线和连字符")
    private String id;

    @NotBlank(message = "子任务标题不能为空")
    @Size(max = 128, message = "子任务标题过长")
    private String title;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式应为 YYYY-MM-DD")
    private String dueDate;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "周起始格式应为 YYYY-MM-DD")
    private String weekStart;

    private Integer sortOrder;
}
