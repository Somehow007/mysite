package io.github.somehow.mysite.journal.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.somehow.mysite.journal.dto.DayRecordDTO;
import io.github.somehow.mysite.journal.dto.DayRecordUpsertReqDTO;
import io.github.somehow.mysite.journal.dto.ExportRespDTO;
import io.github.somehow.mysite.journal.dto.ImportResultDTO;

import java.util.List;

/**
 * 学习手帐记录服务。所有方法强制带 userId 条件做用户隔离（userId 由服务端从登录态解析）。
 * <p>date 全程按 'YYYY-MM-DD' 字符串处理，禁止任何时区转换。</p>
 */
public interface JournalService {

    /** 单日详情（含 learnings 数组）；记录不存在返回 null */
    DayRecordDTO getRecordByDate(Long userId, String date);

    /** month（'YYYY-MM'）优先于 year；两者皆空返回全部，按 date 升序 */
    List<DayRecordDTO> listRecords(Long userId, String month, Integer year);

    /**
     * 创建或更新单日记录（patch 语义）：
     * mood/diary 传了才更新（mood 空串=清除）；learnings 非 null 时全量替换。
     * 更新主表 + 替换 learnings 在同一事务内。
     */
    DayRecordDTO upsertRecord(Long userId, String date, DayRecordUpsertReqDTO request);

    /** 删除整日记录，学习条目经外键级联删除；幂等 */
    void deleteRecord(Long userId, String date);

    /** 日记全文搜索（LIKE），按 updatedAt 倒序 */
    List<DayRecordDTO> searchDiary(Long userId, String keyword);

    /** 导出全部数据，保持既有 v2 JSON 格式 */
    ExportRespDTO exportAll(Long userId);

    /** 导入备份 JSON（兼容 v1 纯数组 / v2 含 customMoods），按 (user_id, date) 幂等 upsert */
    ImportResultDTO importData(Long userId, JsonNode body);
}
