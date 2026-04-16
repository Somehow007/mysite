package io.github.somehow.mysite.controller;

import cn.hutool.system.UserInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.context.UserInfoDTO;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dto.req.user.UserFollowReqDTO;
import io.github.somehow.mysite.dto.req.user.UserLoginReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserRegistryReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;
import io.github.somehow.mysite.security.SecurityUserDetails;
import io.github.somehow.mysite.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 用户管理控制层
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "用户管理控制层")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @Operation(summary = "用户登录")
    @PostMapping("/api/auth/login")
    public Result<Void> login(
            @RequestBody UserLoginReqDTO requestParam,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requestParam.getUsername(), requestParam.getPassword()));
        if (authentication.isAuthenticated()) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 显式创建 Session 并将 SecurityContext 写入，确保响应带 Set-Cookie（跨域时浏览器才能带 Cookie）
            httpRequest.getSession(true);
            new HttpSessionSecurityContextRepository().saveContext(
                    SecurityContextHolder.getContext(),
                    httpRequest,
                    httpResponse);
            SecurityUserDetails userDetails = (SecurityUserDetails) authentication.getPrincipal();
            UserDO userDO = userDetails.getUserDO();
            UserContext.setUser(UserInfoDTO.builder().userId(userDO.getId().toString())
                    .name(userDO.getUsername())
                    .build());
        }

        return Results.success();
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/api/user/current")
    public Result<UserSelectRespDTO> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityUserDetails)) {
            return Results.success(null);
        }
        SecurityUserDetails userDetails = (SecurityUserDetails) authentication.getPrincipal();
        return Results.success(userService.selectUserById(String.valueOf(userDetails.getUserId())));
    }

    @Operation(summary = "用户注册")
    @PostMapping("/registry")
    public Result<Void> registry(@RequestBody UserRegistryReqDTO requestParam) {
        userService.registry(requestParam);
        return Results.success();
    }

    @Operation(summary = "根据用户id获取用户具体信息")
    @GetMapping("/api/user/query/{id}")
    public Result<UserSelectRespDTO> selectUserById(@PathVariable("id") String id) {
        return Results.success(userService.selectUserById(id));
    }

    @Operation(summary = "关注或取消关注用户")
    @PostMapping("/api/user/follow")
    public Result<Void> followUser(@RequestBody UserFollowReqDTO requestParam) {
        userService.followUser(requestParam);
        return Results.success();
    }

    @Operation(summary = "分页获取粉丝信息")
    @GetMapping("/api/user/followees/{id}")
    public Result<IPage<UserPageQueryFollowRespDTO>> selectFollowers(@PathVariable("id") String id) {
        return Results.success(userService.selectFollowers(id));
    }


    @Operation(summary = "分页获取关注的用户信息")
    @GetMapping("/api/user/followers/{id}")
    public Result<IPage<UserPageQueryFollowRespDTO>> selectFollowings(@PathVariable("id") String id) {
        return Results.success(userService.selectFollowings(id));
    }
    
    @Operation(summary = "分页搜索用户")
    @GetMapping("/api/user/search")
    public Result<IPage<UserSearchRespDTO>> searchUsers(UserPageQueryReqDTO requestParam) {
        return Results.success(userService.pageQueryUser(requestParam));
    }
}