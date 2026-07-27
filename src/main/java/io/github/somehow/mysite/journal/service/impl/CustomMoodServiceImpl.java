package io.github.somehow.mysite.journal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.exception.ServiceException;
import io.github.somehow.mysite.journal.dao.entity.SjCustomMoodDO;
import io.github.somehow.mysite.journal.dao.mapper.SjCustomMoodMapper;
import io.github.somehow.mysite.journal.dto.CustomMoodDTO;
import io.github.somehow.mysite.journal.dto.CustomMoodReqDTO;
import io.github.somehow.mysite.journal.dto.DarkColorsDTO;
import io.github.somehow.mysite.journal.service.CustomMoodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomMoodServiceImpl implements CustomMoodService {

    private final SjCustomMoodMapper customMoodMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<CustomMoodDTO> listMoods(Long userId) {
        List<SjCustomMoodDO> list = customMoodMapper.selectList(
                new LambdaQueryWrapper<SjCustomMoodDO>()
                        .eq(SjCustomMoodDO::getUserId, userId)
                        .orderByAsc(SjCustomMoodDO::getCreatedAt));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public CustomMoodDTO createMood(Long userId, CustomMoodReqDTO request) {
        if (!StringUtils.hasText(request.getId())) {
            throw new ClientException("自定义心情 id 不能为空", ErrorCode.PARAM_VALIDATION_ERROR);
        }
        if (customMoodMapper.selectById(request.getId()) != null) {
            throw new ClientException(ErrorCode.JOURNAL_MOOD_DUPLICATE);
        }
        SjCustomMoodDO entity = new SjCustomMoodDO();
        entity.setId(request.getId());
        entity.setUserId(userId);
        entity.setCreatedAt(System.currentTimeMillis());
        applyRequest(entity, request);
        customMoodMapper.insert(entity);
        return toDTO(entity);
    }

    @Override
    public CustomMoodDTO updateMood(Long userId, String moodId, CustomMoodReqDTO request) {
        SjCustomMoodDO entity = findByUser(userId, moodId);
        if (entity == null) {
            throw new ClientException(ErrorCode.JOURNAL_MOOD_NOT_FOUND);
        }
        applyRequest(entity, request);
        customMoodMapper.updateById(entity);
        return toDTO(entity);
    }

    @Override
    public void deleteMood(Long userId, String moodId) {
        customMoodMapper.delete(new LambdaQueryWrapper<SjCustomMoodDO>()
                .eq(SjCustomMoodDO::getId, moodId)
                .eq(SjCustomMoodDO::getUserId, userId));
    }

    @Override
    public int importMoods(Long userId, List<CustomMoodDTO> moods) {
        if (moods == null || moods.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int count = 0;
        for (CustomMoodDTO mood : moods) {
            if (mood == null || !StringUtils.hasText(mood.getId())) {
                throw new ClientException("导入的自定义心情缺少 id", ErrorCode.JOURNAL_IMPORT_INVALID);
            }
            SjCustomMoodDO existing = findByUser(userId, mood.getId());
            if (existing != null) {
                applyDto(existing, mood);
                customMoodMapper.updateById(existing);
            } else {
                SjCustomMoodDO entity = new SjCustomMoodDO();
                entity.setId(mood.getId());
                entity.setUserId(userId);
                entity.setCreatedAt(mood.getCreatedAt() != null ? mood.getCreatedAt() : now);
                applyDto(entity, mood);
                customMoodMapper.insert(entity);
            }
            count++;
        }
        return count;
    }

    // ─── 内部工具 ────────────────────────────────────

    private SjCustomMoodDO findByUser(Long userId, String moodId) {
        return customMoodMapper.selectOne(new LambdaQueryWrapper<SjCustomMoodDO>()
                .eq(SjCustomMoodDO::getId, moodId)
                .eq(SjCustomMoodDO::getUserId, userId));
    }

    private void applyRequest(SjCustomMoodDO entity, CustomMoodReqDTO request) {
        entity.setLabel(request.getLabel());
        entity.setEmoji(request.getEmoji() == null ? "" : request.getEmoji());
        entity.setSolid(request.getSolid());
        entity.setInk(request.getInk());
        entity.setTint(request.getTint());
        entity.setDarkColors(writeJson(request.getDark()));
    }

    private void applyDto(SjCustomMoodDO entity, CustomMoodDTO dto) {
        if (dto.getLabel() != null) {
            entity.setLabel(dto.getLabel());
        }
        entity.setEmoji(dto.getEmoji() == null ? "" : dto.getEmoji());
        if (dto.getSolid() != null) {
            entity.setSolid(dto.getSolid());
        }
        if (dto.getInk() != null) {
            entity.setInk(dto.getInk());
        }
        if (dto.getTint() != null) {
            entity.setTint(dto.getTint());
        }
        DarkColorsDTO dark = dto.getDark() != null
                ? dto.getDark()
                : new DarkColorsDTO(entity.getSolid(), entity.getInk(), entity.getTint());
        entity.setDarkColors(writeJson(dark));
    }

    private CustomMoodDTO toDTO(SjCustomMoodDO entity) {
        CustomMoodDTO dto = new CustomMoodDTO();
        dto.setId(entity.getId());
        dto.setLabel(entity.getLabel());
        dto.setEmoji(entity.getEmoji());
        dto.setSolid(entity.getSolid());
        dto.setInk(entity.getInk());
        dto.setTint(entity.getTint());
        dto.setDark(parseDark(entity));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private DarkColorsDTO parseDark(SjCustomMoodDO entity) {
        if (StringUtils.hasText(entity.getDarkColors())) {
            try {
                return objectMapper.readValue(entity.getDarkColors(), DarkColorsDTO.class);
            } catch (JsonProcessingException e) {
                log.warn("自定义心情 {} 的 dark_colors JSON 解析失败，降级为浅色三档", entity.getId(), e);
            }
        }
        return new DarkColorsDTO(entity.getSolid(), entity.getInk(), entity.getTint());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("心情颜色序列化失败", e, ErrorCode.JOURNAL_ERROR);
        }
    }
}
