package io.github.somehow.mysite.journal.service.impl;

import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.journal.dao.entity.SjCustomMoodDO;
import io.github.somehow.mysite.journal.dao.mapper.SjCustomMoodMapper;
import io.github.somehow.mysite.journal.dto.CustomMoodDTO;
import io.github.somehow.mysite.journal.dto.CustomMoodReqDTO;
import io.github.somehow.mysite.journal.dto.DarkColorsDTO;
import io.github.somehow.mysite.support.TestTableInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 学习手帐·自定义心情 characterization 测试（阶段 2 安全网）。
 * 钉现状：user_id 隔离口径、幂等导入语义、dark_colors 兜底、错误码。
 */
@ExtendWith(MockitoExtension.class)
class CustomMoodServiceImplTest {

    private static final Long UID = 7L;

    @Mock
    private SjCustomMoodMapper customMoodMapper;

    @InjectMocks
    private CustomMoodServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TestTableInfo.initAll();
    }

    @BeforeEach
    void setUpObjectMapper() {
        // ObjectMapper 是纯序列化器，用真实实例（@InjectMocks 会注入 null，手动补）
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    // ─── listMoods ─────────────────────────────────────

    @Test
    void listMoods_returnsMappedDtos() {
        SjCustomMoodDO ok = moodDo("m1", "开心", "#fff", "#111", "#eee");
        ok.setDarkColors("{\"solid\":\"#000\",\"ink\":\"#222\",\"tint\":\"#333\"}");
        SjCustomMoodDO badJson = moodDo("m2", "难过", "#000", "#fff", "#ddd");
        badJson.setDarkColors("{broken json");
        when(customMoodMapper.selectList(any())).thenReturn(List.of(ok, badJson));

        List<CustomMoodDTO> result = service.listMoods(UID);

        assertEquals(2, result.size());
        assertEquals("开心", result.get(0).getLabel());
        assertEquals("#000", result.get(0).getDark().getSolid());      // 正常解析
        // 坏 JSON 兜底：dark 回落为浅色三档
        assertEquals("#000", result.get(1).getDark().getSolid());
        assertEquals("#fff", result.get(1).getDark().getInk());
        assertEquals("#ddd", result.get(1).getDark().getTint());
    }

    // ─── createMood ────────────────────────────────────

    @Test
    void createMood_blankId_throwsValidation() {
        CustomMoodReqDTO req = moodReq(" ", "开心");
        ClientException ex = assertThrows(ClientException.class, () -> service.createMood(UID, req));
        assertEquals(ErrorCode.PARAM_VALIDATION_ERROR.code(), ex.getErrorCode());
    }

    @Test
    void createMood_existingId_throwsDuplicate() {
        when(customMoodMapper.selectById("m1")).thenReturn(moodDo("m1", "旧", "", "", ""));
        ClientException ex = assertThrows(ClientException.class,
                () -> service.createMood(UID, moodReq("m1", "新")));
        assertEquals(ErrorCode.JOURNAL_MOOD_DUPLICATE.code(), ex.getErrorCode());
        verify(customMoodMapper, never()).insert(any(SjCustomMoodDO.class));
    }

    @Test
    void createMood_success_setsUserAndSerializesDark() {
        when(customMoodMapper.selectById("m9")).thenReturn(null);
        CustomMoodReqDTO req = moodReq("m9", "平静");
        req.setSolid("#aaa");
        req.setInk("#bbb");
        req.setTint("#ccc");
        req.setDark(new DarkColorsDTO("#111", "#222", "#333"));

        CustomMoodDTO dto = service.createMood(UID, req);

        ArgumentCaptor<SjCustomMoodDO> captor = ArgumentCaptor.forClass(SjCustomMoodDO.class);
        verify(customMoodMapper).insert(captor.capture());
        SjCustomMoodDO saved = captor.getValue();
        assertEquals(UID, saved.getUserId());
        assertNotNull(saved.getCreatedAt());
        assertTrue(saved.getDarkColors().contains("\"solid\":\"#111\""));
        assertEquals("平静", dto.getLabel());
        assertEquals("#111", dto.getDark().getSolid());
    }

    // ─── updateMood ────────────────────────────────────

    @Test
    void updateMood_notFoundOrNotOwned_throwsNotFound() {
        // findByUser 用 selectOne+eq(userId)：返回 null = 不存在或非本人
        when(customMoodMapper.selectOne(any())).thenReturn(null);
        ClientException ex = assertThrows(ClientException.class,
                () -> service.updateMood(UID, "m1", moodReq("m1", "改名")));
        assertEquals(ErrorCode.JOURNAL_MOOD_NOT_FOUND.code(), ex.getErrorCode());
        verify(customMoodMapper, never()).updateById(any(SjCustomMoodDO.class));
    }

    @Test
    void updateMood_success_appliesRequestFields() {
        SjCustomMoodDO entity = moodDo("m1", "旧名", "#fff", "#111", "#eee");
        when(customMoodMapper.selectOne(any())).thenReturn(entity);

        CustomMoodDTO dto = service.updateMood(UID, "m1", moodReq("m1", "新名"));

        assertEquals("新名", dto.getLabel());
        verify(customMoodMapper).updateById(entity);
    }

    // ─── deleteMood ────────────────────────────────────

    @Test
    void deleteMood_delegatesScopedDelete() {
        service.deleteMood(UID, "m1");
        verify(customMoodMapper).delete(any());
    }

    // ─── importMoods ───────────────────────────────────

    @Test
    void importMoods_nullOrEmpty_returnsZero() {
        assertEquals(0, service.importMoods(UID, null));
        assertEquals(0, service.importMoods(UID, List.of()));
        verifyNoInteractions(customMoodMapper);
    }

    @Test
    void importMoods_missingId_throwsImportInvalid() {
        CustomMoodDTO bad = new CustomMoodDTO();
        bad.setLabel("无id");
        ClientException ex = assertThrows(ClientException.class,
                () -> service.importMoods(UID, List.of(bad)));
        assertEquals(ErrorCode.JOURNAL_IMPORT_INVALID.code(), ex.getErrorCode());
    }

    @Test
    void importMoods_insertAndUpdate_countsBoth() {
        CustomMoodDTO existing = new CustomMoodDTO();
        existing.setId("m1");
        existing.setLabel("已有");
        CustomMoodDTO fresh = new CustomMoodDTO();
        fresh.setId("m2");
        fresh.setLabel("新增");

        // importMoods 按列表顺序逐条 findByUser：m1 命中已有 → update；m2 未命中 → insert
        when(customMoodMapper.selectOne(any()))
                .thenReturn(moodDo("m1", "已有", "", "", ""))
                .thenReturn(null);

        int count = service.importMoods(UID, List.of(existing, fresh));

        assertEquals(2, count);
        verify(customMoodMapper).updateById(any(SjCustomMoodDO.class));
        verify(customMoodMapper).insert(any(SjCustomMoodDO.class));
    }

    @Test
    void importMoods_insert_preservesProvidedCreatedAt() {
        CustomMoodDTO fresh = new CustomMoodDTO();
        fresh.setId("m3");
        fresh.setLabel("带时间");
        fresh.setCreatedAt(1700000000000L);
        when(customMoodMapper.selectOne(any())).thenReturn(null);

        service.importMoods(UID, List.of(fresh));

        ArgumentCaptor<SjCustomMoodDO> captor = ArgumentCaptor.forClass(SjCustomMoodDO.class);
        verify(customMoodMapper).insert(captor.capture());
        assertEquals(1700000000000L, captor.getValue().getCreatedAt());
    }

    // ─── helpers ───────────────────────────────────────

    private SjCustomMoodDO moodDo(String id, String label, String solid, String ink, String tint) {
        SjCustomMoodDO d = new SjCustomMoodDO();
        d.setId(id);
        d.setUserId(UID);
        d.setLabel(label);
        d.setEmoji("😀");
        d.setSolid(solid);
        d.setInk(ink);
        d.setTint(tint);
        d.setCreatedAt(1L);
        return d;
    }

    private CustomMoodReqDTO moodReq(String id, String label) {
        CustomMoodReqDTO req = new CustomMoodReqDTO();
        req.setId(id);
        req.setLabel(label);
        req.setEmoji("🙂");
        return req;
    }
}
