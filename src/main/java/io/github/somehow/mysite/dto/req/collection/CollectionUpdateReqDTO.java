package io.github.somehow.mysite.dto.req.collection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class CollectionUpdateReqDTO {

    @Schema(description = "合集标题")
    @Size(max = 200, message = "合集标题不能超过200个字符")
    private String title;

    @Schema(description = "合集描述")
    @Size(max = 500, message = "合集描述不能超过500个字符")
    private String description;

    @Schema(description = "合集封面图片URL")
    @Size(max = 500, message = "封面图片URL不能超过500个字符")
    private String coverImage;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "计划总篇数：正整数=设置；显式传 null=清除计划；不传该字段=不修改")
    @Min(value = 1, message = "计划总篇数必须大于0")
    @Setter(AccessLevel.NONE)
    private Integer plannedCount;

    /** plannedCount 是否被显式传入（区分「不传=不改」与「传 null=清除」），不参与 JSON 序列化 */
    @JsonIgnore
    private boolean plannedCountProvided;

    public void setPlannedCount(Integer plannedCount) {
        this.plannedCount = plannedCount;
        this.plannedCountProvided = true;
    }

    @Schema(description = "连载状态：0-连载中 1-已完结")
    @Min(value = 0, message = "连载状态只能是 0(连载中) 或 1(已完结)")
    @Max(value = 1, message = "连载状态只能是 0(连载中) 或 1(已完结)")
    private Integer status;

    @Schema(description = "可见性：0-公开 1-私有（仅自己可见）")
    @Min(value = 0, message = "可见性只能是 0(公开) 或 1(私有)")
    @Max(value = 1, message = "可见性只能是 0(公开) 或 1(私有)")
    private Integer visibility;
}
