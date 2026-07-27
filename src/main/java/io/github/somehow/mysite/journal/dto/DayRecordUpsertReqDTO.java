package io.github.somehow.mysite.journal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * PUT /api/journal/records/{date} 请求体（部分字段 patch 语义）：
 * <ul>
 *   <li>mood / diary：传了才更新；空字符串的 mood 视为「清除心情」</li>
 *   <li>learnings：传了则全量替换当日清单（删旧插新），不传则保持原样</li>
 * </ul>
 */
@Data
public class DayRecordUpsertReqDTO {

    /** 预设心情枚举或自定义心情 nanoid；空串=清除 */
    @Size(max = 32, message = "心情值过长")
    private String mood;

    /** Markdown 原文 */
    private String diary;

    /** 学习清单；非 null 时全量替换 */
    @Valid
    private List<LearningItemDTO> learnings;
}
