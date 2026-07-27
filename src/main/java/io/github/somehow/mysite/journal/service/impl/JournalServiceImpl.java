package io.github.somehow.mysite.journal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.journal.dao.entity.SjCustomMoodDO;
import io.github.somehow.mysite.journal.dao.entity.SjDayRecordDO;
import io.github.somehow.mysite.journal.dao.entity.SjLearningItemDO;
import io.github.somehow.mysite.journal.dao.mapper.SjCustomMoodMapper;
import io.github.somehow.mysite.journal.dao.mapper.SjDayRecordMapper;
import io.github.somehow.mysite.journal.dao.mapper.SjLearningItemMapper;
import io.github.somehow.mysite.journal.dto.CustomMoodDTO;
import io.github.somehow.mysite.journal.dto.DayRecordDTO;
import io.github.somehow.mysite.journal.dto.DayRecordUpsertReqDTO;
import io.github.somehow.mysite.journal.dto.ExportRespDTO;
import io.github.somehow.mysite.journal.dto.ImportResultDTO;
import io.github.somehow.mysite.journal.dto.LearningItemDTO;
import io.github.somehow.mysite.journal.service.CustomMoodService;
import io.github.somehow.mysite.journal.service.JournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    /** 预设心情枚举，与前端 MoodType 一致；自定义心情存 nanoid，与预设单词天然不碰撞 */
    private static final Set<String> PRESET_MOODS =
            Set.of("happy", "calm", "sad", "inspired", "anxious", "tired");
    private static final int SEARCH_LIMIT = 200;

    private final SjDayRecordMapper dayRecordMapper;
    private final SjLearningItemMapper learningItemMapper;
    private final SjCustomMoodMapper customMoodMapper;
    private final CustomMoodService customMoodService;
    private final ObjectMapper objectMapper;

    @Override
    public DayRecordDTO getRecordByDate(Long userId, String date) {
        validateDate(date);
        SjDayRecordDO record = findRecord(userId, date);
        if (record == null) {
            return null;
        }
        Map<Long, List<LearningItemDTO>> learnings = loadLearnings(List.of(record.getId()));
        return buildDTO(record, learnings.getOrDefault(record.getId(), List.of()));
    }

    @Override
    public List<DayRecordDTO> listRecords(Long userId, String month, Integer year) {
        LambdaQueryWrapper<SjDayRecordDO> wrapper = new LambdaQueryWrapper<SjDayRecordDO>()
                .eq(SjDayRecordDO::getUserId, userId);
        if (StringUtils.hasText(month)) {
            if (!MONTH_PATTERN.matcher(month).matches()) {
                throw new ClientException("月份格式无效，应为 YYYY-MM", ErrorCode.JOURNAL_DATE_INVALID);
            }
            wrapper.likeRight(SjDayRecordDO::getDate, month);
        } else if (year != null) {
            wrapper.likeRight(SjDayRecordDO::getDate, year + "-");
        }
        wrapper.orderByAsc(SjDayRecordDO::getDate);

        List<SjDayRecordDO> records = dayRecordMapper.selectList(wrapper);
        Map<Long, List<LearningItemDTO>> learnings =
                loadLearnings(records.stream().map(SjDayRecordDO::getId).toList());
        return records.stream()
                .map(r -> buildDTO(r, learnings.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DayRecordDTO upsertRecord(Long userId, String date, DayRecordUpsertReqDTO request) {
        validateDate(date);
        long now = System.currentTimeMillis();
        SjDayRecordDO record = findRecord(userId, date);

        if (record == null) {
            record = new SjDayRecordDO();
            record.setUserId(userId);
            record.setDate(date);
            record.setMood(normalizeAndValidateMood(userId, request.getMood()));
            record.setDiary(request.getDiary());
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            dayRecordMapper.insert(record);
        } else {
            // patch 语义：字段为 null 表示「不修改」；mood 空串表示「清除心情」
            if (request.getMood() != null) {
                record.setMood(normalizeAndValidateMood(userId, request.getMood()));
            }
            if (request.getDiary() != null) {
                record.setDiary(request.getDiary());
            }
            record.setUpdatedAt(now);
            dayRecordMapper.updateById(record);
        }

        if (request.getLearnings() != null) {
            replaceLearnings(record.getId(), request.getLearnings());
        }
        return buildDTO(record, loadItems(record.getId()));
    }

    @Override
    public void deleteRecord(Long userId, String date) {
        validateDate(date);
        // 学习条目经外键 ON DELETE CASCADE 级联清理；记录不存在时删除 0 行，幂等返回成功
        dayRecordMapper.delete(new LambdaQueryWrapper<SjDayRecordDO>()
                .eq(SjDayRecordDO::getUserId, userId)
                .eq(SjDayRecordDO::getDate, date));
    }

    @Override
    public List<DayRecordDTO> searchDiary(Long userId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<SjDayRecordDO> records = dayRecordMapper.selectList(
                new LambdaQueryWrapper<SjDayRecordDO>()
                        .eq(SjDayRecordDO::getUserId, userId)
                        .like(SjDayRecordDO::getDiary, keyword)
                        .orderByDesc(SjDayRecordDO::getUpdatedAt)
                        .last("LIMIT " + SEARCH_LIMIT));
        Map<Long, List<LearningItemDTO>> learnings =
                loadLearnings(records.stream().map(SjDayRecordDO::getId).toList());
        return records.stream()
                .map(r -> buildDTO(r, learnings.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Override
    public ExportRespDTO exportAll(Long userId) {
        List<DayRecordDTO> records = listRecords(userId, null, null);
        List<CustomMoodDTO> moods = customMoodService.listMoods(userId);
        return new ExportRespDTO(2, Instant.now().toString(), records, moods);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultDTO importData(Long userId, JsonNode body) {
        if (body == null || body.isNull()) {
            throw new ClientException(ErrorCode.JOURNAL_IMPORT_INVALID);
        }
        List<DayRecordDTO> records;
        List<CustomMoodDTO> moods = new ArrayList<>();
        try {
            if (body.isArray()) {
                // v1 格式：纯 records 数组
                records = objectMapper.convertValue(body, new TypeReference<List<DayRecordDTO>>() {
                });
            } else {
                // v2 格式：{ version, exportedAt, records, customMoods }
                JsonNode recordsNode = body.get("records");
                records = recordsNode == null || recordsNode.isNull()
                        ? new ArrayList<>()
                        : objectMapper.convertValue(recordsNode, new TypeReference<List<DayRecordDTO>>() {
                        });
                JsonNode moodsNode = body.get("customMoods");
                if (moodsNode != null && !moodsNode.isNull()) {
                    moods = objectMapper.convertValue(moodsNode, new TypeReference<List<CustomMoodDTO>>() {
                    });
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ClientException("导入数据解析失败：" + e.getMessage(), ErrorCode.JOURNAL_IMPORT_INVALID);
        }

        // 先导入自定义心情，再导入记录：记录的 mood 字段会校验心情存在性，
        // 若记录先于其引用的自定义心情导入，整批导入会因校验失败回滚
        int moodCount = customMoodService.importMoods(userId, moods);
        int recordCount = 0;
        for (DayRecordDTO record : records) {
            importRecord(userId, record);
            recordCount++;
        }
        log.info("学习手帐导入完成：user={}，records={}，moods={}", userId, recordCount, moodCount);
        return new ImportResultDTO(recordCount, moodCount);
    }

    // ─── 内部工具 ────────────────────────────────────

    /** 导入单条记录：按 (user_id, date) upsert，保留原始 createdAt/updatedAt */
    private void importRecord(Long userId, DayRecordDTO record) {
        if (record == null || record.getDate() == null || !DATE_PATTERN.matcher(record.getDate()).matches()) {
            throw new ClientException("导入数据包含非法日期", ErrorCode.JOURNAL_IMPORT_INVALID);
        }
        long now = System.currentTimeMillis();
        SjDayRecordDO existing = findRecord(userId, record.getDate());
        if (existing == null) {
            existing = new SjDayRecordDO();
            existing.setUserId(userId);
            existing.setDate(record.getDate());
            existing.setMood(normalizeAndValidateMood(userId, record.getMood()));
            existing.setDiary(record.getDiary());
            existing.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt() : now);
            existing.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt() : now);
            dayRecordMapper.insert(existing);
        } else {
            existing.setMood(normalizeAndValidateMood(userId, record.getMood()));
            existing.setDiary(record.getDiary());
            if (record.getCreatedAt() != null) {
                existing.setCreatedAt(record.getCreatedAt());
            }
            existing.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt() : now);
            dayRecordMapper.updateById(existing);
        }
        replaceLearnings(existing.getId(), record.getLearnings() == null ? List.of() : record.getLearnings());
    }

    private void validateDate(String date) {
        if (date == null || !DATE_PATTERN.matcher(date).matches()) {
            throw new ClientException(ErrorCode.JOURNAL_DATE_INVALID);
        }
    }

    /** mood 空值归一化为 null；非空时校验为预设枚举或该用户的自定义心情 */
    private String normalizeAndValidateMood(Long userId, String mood) {
        if (!StringUtils.hasText(mood)) {
            return null;
        }
        if (!PRESET_MOODS.contains(mood)) {
            Long count = customMoodMapper.selectCount(new LambdaQueryWrapper<SjCustomMoodDO>()
                    .eq(SjCustomMoodDO::getId, mood)
                    .eq(SjCustomMoodDO::getUserId, userId));
            if (count == null || count == 0) {
                throw new ClientException(ErrorCode.JOURNAL_MOOD_INVALID);
            }
        }
        return mood;
    }

    private SjDayRecordDO findRecord(Long userId, String date) {
        // uk_user_date 唯一索引保证至多一行
        return dayRecordMapper.selectOne(new LambdaQueryWrapper<SjDayRecordDO>()
                .eq(SjDayRecordDO::getUserId, userId)
                .eq(SjDayRecordDO::getDate, date));
    }

    /** 全量替换某日学习清单（条目数量小，删旧插新永远一致，省去 diff） */
    private void replaceLearnings(Long recordId, List<LearningItemDTO> learnings) {
        learningItemMapper.delete(new LambdaQueryWrapper<SjLearningItemDO>()
                .eq(SjLearningItemDO::getRecordId, recordId));
        for (int i = 0; i < learnings.size(); i++) {
            LearningItemDTO item = learnings.get(i);
            if (item == null
                    || !StringUtils.hasText(item.getId())
                    || !StringUtils.hasText(item.getSubject())
                    || item.getDurationMin() == null
                    || item.getDurationMin() < 1
                    || !StringUtils.hasText(item.getColor())) {
                throw new ClientException("学习条目数据不完整", ErrorCode.JOURNAL_IMPORT_INVALID);
            }
            SjLearningItemDO entity = new SjLearningItemDO();
            entity.setRecordId(recordId);
            entity.setClientId(item.getId());
            entity.setSubject(item.getSubject());
            entity.setDurationMin(item.getDurationMin());
            entity.setNote(item.getNote() == null ? "" : item.getNote());
            entity.setColor(item.getColor());
            entity.setSortOrder(i);
            learningItemMapper.insert(entity);
        }
    }

    private Map<Long, List<LearningItemDTO>> loadLearnings(Collection<Long> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        List<SjLearningItemDO> items = learningItemMapper.selectList(
                new LambdaQueryWrapper<SjLearningItemDO>()
                        .in(SjLearningItemDO::getRecordId, recordIds)
                        .orderByAsc(SjLearningItemDO::getSortOrder));
        return items.stream().collect(Collectors.groupingBy(
                SjLearningItemDO::getRecordId,
                Collectors.mapping(this::toLearningDTO, Collectors.toList())));
    }

    private List<LearningItemDTO> loadItems(Long recordId) {
        return loadLearnings(List.of(recordId)).getOrDefault(recordId, List.of());
    }

    private DayRecordDTO buildDTO(SjDayRecordDO record, List<LearningItemDTO> learnings) {
        DayRecordDTO dto = new DayRecordDTO();
        dto.setDate(record.getDate());
        dto.setMood(record.getMood());
        dto.setDiary(record.getDiary());
        dto.setLearnings(learnings);
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        return dto;
    }

    private LearningItemDTO toLearningDTO(SjLearningItemDO entity) {
        LearningItemDTO dto = new LearningItemDTO();
        dto.setId(entity.getClientId());
        dto.setSubject(entity.getSubject());
        dto.setDurationMin(entity.getDurationMin());
        dto.setNote(entity.getNote());
        dto.setColor(entity.getColor());
        return dto;
    }
}
