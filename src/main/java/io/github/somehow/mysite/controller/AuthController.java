package io.github.somehow.mysite.controller;

import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.auth.ChangePasswordReqDTO;
import io.github.somehow.mysite.dto.req.auth.LoginReqDTO;
import io.github.somehow.mysite.dto.req.auth.RefreshTokenReqDTO;
import io.github.somehow.mysite.dto.req.auth.RegisterReqDTO;
import io.github.somehow.mysite.dto.resp.auth.LoginRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;
import io.github.somehow.mysite.security.SecurityUserDetails;
import io.github.somehow.mysite.service.AuthService;
import io.github.somehow.mysite.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "认证管理")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/v1/auth/login")
    public Result<LoginRespDTO> login(@Valid @RequestBody LoginReqDTO requestParam) {
        return Results.success(authService.login(requestParam));
    }

    @Operation(summary = "用户注销")
    @PostMapping("/v1/auth/logout")
    public Result<Void> logout(@AuthenticationPrincipal SecurityUserDetails userDetails) {
        if (userDetails != null) {
            authService.logout(userDetails.getUserId());
        }
        return Results.success();
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/v1/auth/refresh")
    public Result<LoginRespDTO> refreshToken(@Valid @RequestBody RefreshTokenReqDTO requestParam) {
        return Results.success(authService.refreshToken(requestParam));
    }

    @Operation(summary = "用户注册")
    @PostMapping("/v1/auth/register")
    public Result<Void> register(@Valid @RequestBody RegisterReqDTO requestParam) {
        authService.register(requestParam);
        return Results.success();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/v1/auth/me")
    public Result<UserSelectRespDTO> getCurrentUser(@AuthenticationPrincipal SecurityUserDetails userDetails) {
        if (userDetails == null) {
            return Results.success(null);
        }
        return Results.success(userService.selectUserById(userDetails.getUserId().toString()));
    }

    @Operation(summary = "修改密码")
    @PostMapping("/v1/auth/change-password")
    public Result<Void> changePassword(
            @AuthenticationPrincipal SecurityUserDetails userDetails,
            @Valid @RequestBody ChangePasswordReqDTO requestParam) {
        if (userDetails == null) {
            return Results.success();
        }
        userService.changePassword(userDetails.getUserId(), requestParam);
        return Results.success();
    }
}
