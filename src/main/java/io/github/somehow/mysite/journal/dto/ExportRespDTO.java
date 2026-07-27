package io.github.somehow.mysite.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/journal/export 响应，保持手帐既有导出 JSON 格式（v2）：
 * <pre>{ "version": 2, "exportedAt": "…ISO…", "records": […], "customMoods": […] }</pre>
 * 与前端 db.ts 的 ExportData 结构一致，导出文件可直接回导。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportRespDTO {

    private Integer version;

    /** ISO-8601 导出时间 */
    private String exportedAt;

    private List<DayRecordDTO> records;

    private List<CustomMoodDTO> customMoods;
}
