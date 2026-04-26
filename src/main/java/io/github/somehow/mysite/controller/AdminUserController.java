package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.admin.UserRoleUpdateReqDTO;
import io.github.somehow.mysite.dto.req.admin.UserStatusUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.admin.AdminUserRespDTO;
import io.github.somehow.mysite.dto.resp.admin.UserOperationLogRespDTO;
import io.github.somehow.mysite.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/users")
@Tag(name = "用户管理（开发者）")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "分页查询用户列表")
    @GetMapping
    public Result<IPage<AdminUserRespDTO>> listUsers(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Results.success(adminUserService.listUsers(current, size, keyword));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<AdminUserRespDTO> getUserDetail(@PathVariable Long id) {
        return Results.success(adminUserService.getUserDetail(id));
    }

    @Operation(summary = "修改用户角色")
    @PutMapping("/{id}/role")
    public Result<Void> updateUserRole(@PathVariable Long id,
                                       @Valid @RequestBody UserRoleUpdateReqDTO requestParam) {
        String operatorId = UserContext.getUserId();
        if (operatorId != null && Long.parseLong(operatorId) == id) {
            throw new ClientException("不能修改自己的角色");
        }
        adminUserService.updateUserRole(id, requestParam, operatorId);
        return Results.success();
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id,
                                         @Valid @RequestBody UserStatusUpdateReqDTO requestParam) {
        String operatorId = UserContext.getUserId();
        if (operatorId != null && Long.parseLong(operatorId) == id) {
            throw new ClientException("不能修改自己的状态");
        }
        adminUserService.updateUserStatus(id, requestParam, operatorId);
        return Results.success();
    }

    @Operation(summary = "删除用户（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        String operatorId = UserContext.getUserId();
        if (operatorId != null && Long.parseLong(operatorId) == id) {
            throw new ClientException("不能删除自己");
        }
        adminUserService.deleteUser(id, operatorId);
        return Results.success();
    }

    @Operation(summary = "查询操作日志")
    @GetMapping("/operation-logs")
    public Result<IPage<UserOperationLogRespDTO>> listOperationLogs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long targetUserId) {
        return Results.success(adminUserService.listOperationLogs(current, size, targetUserId));
    }
}
