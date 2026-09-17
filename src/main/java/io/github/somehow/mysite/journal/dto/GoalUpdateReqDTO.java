package io.github.somehow.mysite.journal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新月计划目标（patch：null 表示不改）。不可改 type / period。
 */
@Data
public class GoalUpdateReqDTO {

    @Size(max = 128, message = "目标标题过长")
    private String title;

    private Integer targetValue;

    @Size(max = 16, message = "单位过长")
    private String unit;

    @Pattern(regexp = DarkColorsDTO.COLOR_PATTERN, message = "颜色格式应为 #RRGGBB")
    private String color;

    @Size(max = 512, message = "说明过长")
    private String note;

    private Integer sortOrder;

    @Pattern(regexp = "^(ACTIVE|ARCHIVED)$", message = "状态须为 ACTIVE 或 ARCHIVED")
    private String status;
}
