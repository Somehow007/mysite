package io.github.somehow.mysite.journal.controller;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.journal.dto.CompleteTaskReqDTO;
import io.github.somehow.mysite.journal.dto.GoalCheckInReqDTO;
import io.github.somehow.mysite.journal.dto.GoalCreateReqDTO;
import io.github.somehow.mysite.journal.dto.GoalDetailDTO;
import io.github.somehow.mysite.journal.dto.GoalMonthDTO;
import io.github.somehow.mysite.journal.dto.GoalTaskReqDTO;
import io.github.somehow.mysite.journal.dto.GoalUpdateReqDTO;
import io.github.somehow.mysite.journal.dto.TodayGoalsDTO;
import io.github.somehow.mysite.journal.service.JournalGoalService;
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

/**
 * 学习手帐月计划 / 任务清单。仅 ADMIN，user_id 从登录态解析。
 */
@RestController
@RequestMapping("/api/journal/goals")
@RequiredArgsConstructor
@Tag(name = "学习手帐-月计划", description = "月目标、子任务与打卡，仅管理员")
public class JournalGoalController {

    private final JournalGoalService journalGoalService;

    @Operation(summary = "某月目标列表（含每项目标进度、月平均、日历标记）")
    @GetMapping
    public Result<GoalMonthDTO> listByPeriod(@RequestParam String period) {
        return Results.success(journalGoalService.listByPeriod(currentUserId(), period));
    }

    @Operation(summary = "某日待办：数量型建议量 + 清单到期/过期项")
    @GetMapping("/today")
    public Result<TodayGoalsDTO> listToday(@RequestParam(required = false) String date) {
        return Results.success(journalGoalService.listToday(currentUserId(), date));
    }

    @Operation(summary = "目标详情（子任务或打卡 + 周滚动）")
    @GetMapping("/{id}")
    public Result<GoalDetailDTO> getGoal(@PathVariable String id) {
        return Results.success(journalGoalService.getGoal(currentUserId(), id));
    }

    @Operation(summary = "创建目标（id 由前端 nanoid 生成）")
    @PostMapping
    public Result<GoalDetailDTO> createGoal(@Valid @RequestBody GoalCreateReqDTO request) {
        return Results.success(journalGoalService.createGoal(currentUserId(), request));
    }

    @Operation(summary = "更新目标（patch）")
    @PutMapping("/{id}")
    public Result<GoalDetailDTO> updateGoal(
            @PathVariable String id,
            @Valid @RequestBody GoalUpdateReqDTO request) {
        return Results.success(journalGoalService.updateGoal(currentUserId(), id, request));
    }

    @Operation(summary = "删除目标（子任务与打卡级联，幂等）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteGoal(@PathVariable String id) {
        journalGoalService.deleteGoal(currentUserId(), id);
        return Results.success();
    }

    @Operation(summary = "清单型：新增子任务")
    @PostMapping("/{id}/tasks")
    public Result<GoalDetailDTO> addTask(
            @PathVariable String id,
            @Valid @RequestBody GoalTaskReqDTO request) {
        return Results.success(journalGoalService.addTask(currentUserId(), id, request));
    }

    @Operation(summary = "清单型：更新子任务")
    @PutMapping("/{id}/tasks/{taskId}")
    public Result<GoalDetailDTO> updateTask(
            @PathVariable String id,
            @PathVariable String taskId,
            @Valid @RequestBody GoalTaskReqDTO request) {
        return Results.success(journalGoalService.updateTask(currentUserId(), id, taskId, request));
    }

    @Operation(summary = "清单型：删除子任务")
    @DeleteMapping("/{id}/tasks/{taskId}")
    public Result<GoalDetailDTO> deleteTask(@PathVariable String id, @PathVariable String taskId) {
        return Results.success(journalGoalService.deleteTask(currentUserId(), id, taskId));
    }

    @Operation(summary = "清单型：勾选或取消子任务")
    @PostMapping("/{id}/tasks/{taskId}/complete")
    public Result<GoalDetailDTO> completeTask(
            @PathVariable String id,
            @PathVariable String taskId,
            @RequestBody(required = false) CompleteTaskReqDTO request) {
        CompleteTaskReqDTO body = request == null ? new CompleteTaskReqDTO() : request;
        return Results.success(journalGoalService.completeTask(currentUserId(), id, taskId, body));
    }

    @Operation(summary = "数量型：当日打卡 upsert")
    @PutMapping("/{id}/checkins/{date}")
    public Result<GoalDetailDTO> upsertCheckIn(
            @PathVariable String id,
            @PathVariable String date,
            @Valid @RequestBody GoalCheckInReqDTO request) {
        return Results.success(journalGoalService.upsertCheckIn(currentUserId(), id, date, request));
    }

    @Operation(summary = "数量型：取消当日打卡")
    @DeleteMapping("/{id}/checkins/{date}")
    public Result<GoalDetailDTO> deleteCheckIn(@PathVariable String id, @PathVariable String date) {
        return Results.success(journalGoalService.deleteCheckIn(currentUserId(), id, date));
    }

    @Operation(summary = "结转到下月（COUNT 重置打卡；清单只带未完成子任务）")
    @PostMapping("/{id}/carry-over")
    public Result<GoalDetailDTO> carryOver(@PathVariable String id) {
        return Results.success(journalGoalService.carryOver(currentUserId(), id));
    }

    private static Long currentUserId() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new ClientException(ErrorCode.AUTH_ERROR);
        }
        return Long.valueOf(userId);
    }
}
