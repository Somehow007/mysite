package io.github.somehow.mysite.journal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学习条目 DTO，与前端 LearningItem 类型逐字段对齐：
 * <pre>
 * interface LearningItem { id: string; subject: string; durationMin: number; note: string; color: string; }
 * </pre>
 * id 即前端 nanoid，落库为 sj_learning_item.client_id（数据库主键与前端 id 解耦）。
 */
@Data
public class LearningItemDTO {

    /** 前端 nanoid */
    @NotBlank(message = "学习条目 id 不能为空")
    @Size(max = 21, message = "学习条目 id 过长")
    private String id;

    @NotBlank(message = "学科名称不能为空")
    @Size(max = 64, message = "学科名称过长")
    private String subject;

    @NotNull(message = "学习时长不能为空")
    @Min(value = 1, message = "学习时长必须大于 0")
    private Integer durationMin;

    @Size(max = 512, message = "备注过长")
    private String note = "";

    @NotBlank(message = "条目颜色不能为空")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "颜色格式应为 #RRGGBB")
    private String color;
}
