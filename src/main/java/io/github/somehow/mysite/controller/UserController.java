package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.user.UserFollowReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import io.github.somehow.mysite.dto.req.user.UserRegistryReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;
import io.github.somehow.mysite.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制层
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "用户管理控制层")
public class UserController {

    private final UserService userService;

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
    }}
