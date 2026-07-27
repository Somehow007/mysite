package io.github.somehow.mysite.journal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.journal.dto.DayRecordDTO;
import io.github.somehow.mysite.journal.dto.DayRecordUpsertReqDTO;
import io.github.somehow.mysite.journal.dto.ExportRespDTO;
import io.github.somehow.mysite.journal.dto.ImportResultDTO;
import io.github.somehow.mysite.journal.service.JournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习手帐（花期 Blossom）数据接口，前缀 /api/journal。
 * <p>仅 ADMIN 可访问（WebSecurityConfig 中 hasRole("ADMIN")）；
 * user_id 一律从登录态（UserContext）解析，前端不传。</p>
 */
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
@Tag(name = "学习手帐", description = "学习手帐（花期 Blossom）记录接口，仅管理员")
public class JournalController {

    private final JournalService journalService;

    @Operation(summary = "单日记录详情（含学习清单）")
    @GetMapping("/records/{date}")
    public Result<DayRecordDTO> getRecord(@PathVariable String date) {
        return Results.success(journalService.getRecordByDate(currentUserId(), date));
    }

    @Operation(summary = "按月/年查询记录（month=YYYY-MM 优先于 year；皆空返回全部）")
    @GetMapping("/records")
    public Result<List<DayRecordDTO>> listRecords(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        return Results.success(journalService.listRecords(currentUserId(), month, year));
    }

    @Operation(summary = "创建或更新单日记录（upsert，patch 语义）")
    @PutMapping("/records/{date}")
    public Result<DayRecordDTO> upsertRecord(
            @PathVariable String date,
            @Valid @RequestBody DayRecordUpsertReqDTO request) {
        return Results.success(journalService.upsertRecord(currentUserId(), date, request));
    }

    @Operation(summary = "删除整日记录（学习清单级联删除，幂等）")
    @DeleteMapping("/records/{date}")
    public Result<Void> deleteRecord(@PathVariable String date) {
        journalService.deleteRecord(currentUserId(), date);
        return Results.success();
    }

    @Operation(summary = "日记全文搜索（按更新时间倒序）")
    @GetMapping("/search")
    public Result<List<DayRecordDTO>> search(@RequestParam String keyword) {
        return Results.success(journalService.searchDiary(currentUserId(), keyword));
    }

    @Operation(summary = "导入备份 JSON（兼容 v1 纯数组 / v2 含 customMoods，幂等 upsert）")
    @PostMapping("/import")
    public Result<ImportResultDTO> importData(@RequestBody JsonNode body) {
        return Results.success(journalService.importData(currentUserId(), body));
    }

    @Operation(summary = "导出全部数据（保持既有 v2 JSON 格式）")
    @GetMapping("/export")
    public Result<ExportRespDTO> exportAll() {
        return Results.success(journalService.exportAll(currentUserId()));
    }

    /** 安全配置保证 /api/journal/** 必须认证，这里仅作防御性兜底 */
    private static Long currentUserId() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new ClientException(ErrorCode.AUTH_ERROR);
        }
        return Long.valueOf(userId);
    }
}
