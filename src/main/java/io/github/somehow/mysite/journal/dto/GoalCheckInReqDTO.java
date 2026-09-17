package io.github.somehow.mysite.journal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数量型当日打卡 upsert。
 */
@Data
public class GoalCheckInReqDTO {

    @Size(max = 21, message = "打卡 id 过长")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "打卡 id 只能包含字母、数字、下划线和连字符")
    private String id;

    @NotNull(message = "完成数量不能为空")
    @Min(value = 1, message = "完成数量必须大于 0")
    private Integer quantity;

    @Min(value = 1, message = "时长必须大于 0")
    private Integer durationMin;

    @Size(max = 512, message = "感想过长")
    private String note = "";

    private Boolean syncLearning;
}
