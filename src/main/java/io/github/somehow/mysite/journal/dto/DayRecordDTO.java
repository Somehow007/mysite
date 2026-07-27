package io.github.somehow.mysite.journal.dto;

import lombok.Data;

import java.util.List;

/**
 * 单日完整记录 DTO，与前端 DayRecord 类型逐字段对齐：
 * <pre>
 * interface DayRecord {
 *   id?: number; date: string; mood: string | null;
 *   learnings: LearningItem[]; diary: string; createdAt: number; updatedAt: number;
 * }
 * </pre>
 * 数据库自增主键不出现在 API 中（前端以 date 为键），createdAt/updatedAt 为 Unix 毫秒，
 * 在 JS 安全整数范围内，直接以 number 传输。
 */
@Data
public class DayRecordDTO {

    /** YYYY-MM-DD，用户本地日历日 */
    private String date;

    /** 预设心情枚举或自定义心情 nanoid，null=未记录 */
    private String mood;

    private List<LearningItemDTO> learnings;

    /** Markdown 原文 */
    private String diary;

    /** Unix 毫秒 */
    private Long createdAt;

    /** Unix 毫秒 */
    private Long updatedAt;
}
