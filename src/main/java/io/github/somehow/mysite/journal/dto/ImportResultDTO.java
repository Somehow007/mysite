package io.github.somehow.mysite.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/journal/import 响应：实际写入的记录数与自定义心情数。
 * 导入按 (user_id, date) / 心情 nanoid 幂等 upsert，重复导入不产生重复数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {

    private Integer recordsImported;

    private Integer moodsImported;
}
