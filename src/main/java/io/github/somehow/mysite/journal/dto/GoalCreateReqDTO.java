package io.github.somehow.mysite.journal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建月计划目标。id 由前端 nanoid 生成。
 */
@Data
public class GoalCreateReqDTO {

    @NotBlank(message = "目标 id 不能为空")
    @Size(max = 21, message = "目标 id 过长")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "目标 id 只能包含字母、数字、下划线和连字符")
    private String id;

    @NotBlank(message = "月份不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "月份格式应为 YYYY-MM")
    private String period;

    @NotBlank(message = "目标标题不能为空")
    @Size(max = 128, message = "目标标题过长")
    private String title;

    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "^(COUNT|CHECKLIST)$", message = "目标类型须为 COUNT 或 CHECKLIST")
    private String type;

    private Integer targetValue;

    @Size(max = 16, message = "单位过长")
    private String unit;

    @NotBlank(message = "颜色不能为空")
    @Pattern(regexp = DarkColorsDTO.COLOR_PATTERN, message = "颜色格式应为 #RRGGBB")
    private String color;

    @Size(max = 512, message = "说明过长")
    private String note = "";

    private Integer sortOrder;
}
