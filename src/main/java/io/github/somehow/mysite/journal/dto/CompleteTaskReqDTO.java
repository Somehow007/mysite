package io.github.somehow.mysite.journal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 勾选/取消清单子任务。done 为 null 时按当前状态取反。
 */
@Data
public class CompleteTaskReqDTO {

    private Boolean done;

    @Min(value = 1, message = "时长必须大于 0")
    private Integer durationMin;

    private String reflection;

    /** 是否把时长同步到当日学习台账 */
    private Boolean syncLearning;

    /** 同步台账所用日期，缺省为到期日或今天 */
    private String date;
}
