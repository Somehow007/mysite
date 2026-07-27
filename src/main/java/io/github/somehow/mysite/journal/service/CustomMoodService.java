package io.github.somehow.mysite.journal.service;

import io.github.somehow.mysite.journal.dto.CustomMoodDTO;
import io.github.somehow.mysite.journal.dto.CustomMoodReqDTO;

import java.util.List;

/**
 * 自定义心情服务。所有方法强制带 userId 条件做用户隔离。
 */
public interface CustomMoodService {

    /** 按创建时间升序 */
    List<CustomMoodDTO> listMoods(Long userId);

    CustomMoodDTO createMood(Long userId, CustomMoodReqDTO request);

    CustomMoodDTO updateMood(Long userId, String moodId, CustomMoodReqDTO request);

    /** 幂等：心情不存在也返回成功 */
    void deleteMood(Long userId, String moodId);

    /** 导入用：按 nanoid 幂等 upsert，保留原 createdAt。返回写入数量 */
    int importMoods(Long userId, List<CustomMoodDTO> moods);
}
