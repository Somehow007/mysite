package io.github.somehow.mysite.journal.controller;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.journal.dto.CustomMoodDTO;
import io.github.somehow.mysite.journal.dto.CustomMoodReqDTO;
import io.github.somehow.mysite.journal.service.CustomMoodService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习手帐自定义心情接口。仅 ADMIN 可访问，user_id 从登录态解析。
 */
@RestController
@RequestMapping("/api/journal/moods/custom")
@RequiredArgsConstructor
@Tag(name = "学习手帐-自定义心情", description = "自定义心情 CRUD，仅管理员")
public class JournalMoodController {

    private final CustomMoodService customMoodService;

    @Operation(summary = "自定义心情列表（按创建时间升序）")
    @GetMapping
    public Result<List<CustomMoodDTO>> listMoods() {
        return Results.success(customMoodService.listMoods(currentUserId()));
    }

    @Operation(summary = "新增自定义心情（id 由前端 nanoid 生成）")
    @PostMapping
    public Result<CustomMoodDTO> createMood(@Valid @RequestBody CustomMoodReqDTO request) {
        return Results.success(customMoodService.createMood(currentUserId(), request));
    }

    @Operation(summary = "更新自定义心情")
    @PutMapping("/{id}")
    public Result<CustomMoodDTO> updateMood(
            @PathVariable String id,
            @Valid @RequestBody CustomMoodReqDTO request) {
        return Results.success(customMoodService.updateMood(currentUserId(), id, request));
    }

    @Operation(summary = "删除自定义心情（幂等）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteMood(@PathVariable String id) {
        customMoodService.deleteMood(currentUserId(), id);
        return Results.success();
    }

    private static Long currentUserId() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new ClientException(ErrorCode.AUTH_ERROR);
        }
        return Long.valueOf(userId);
    }
}
