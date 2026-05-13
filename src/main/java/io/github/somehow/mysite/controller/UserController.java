package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.user.UserFollowReqDTO;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;
import io.github.somehow.mysite.security.SecurityUserDetails;
import io.github.somehow.mysite.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "用户管理")
public class UserController {

    private final UserService userService;

    @Operation(summary = "根据用户id获取用户具体信息")
    @GetMapping("/v1/users/{id}")
    public Result<UserSelectRespDTO> selectUserById(@PathVariable("id") String id) {
        return Results.success(userService.selectUserById(id));
    }

    @Operation(summary = "更新当前用户信息")
    @PutMapping("/v1/users/me")
    public Result<Void> updateCurrentUser(@AuthenticationPrincipal SecurityUserDetails userDetails,
                                          @RequestBody io.github.somehow.mysite.dto.req.user.UserUpdateReqDTO requestParam) {
        if (userDetails == null) {
            return Results.success();
        }
        requestParam.setUserId(userDetails.getUserId().toString());
        userService.updateUser(requestParam);
        return Results.success();
    }

    @Operation(summary = "关注或取消关注用户")
    @PostMapping("/v1/users/follow")
    public Result<Void> followUser(@RequestBody UserFollowReqDTO requestParam) {
        userService.followUser(requestParam);
        return Results.success();
    }

    @Operation(summary = "分页获取粉丝信息")
    @GetMapping("/v1/users/{id}/followers")
    public Result<IPage<UserPageQueryFollowRespDTO>> selectFollowers(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Results.success(userService.selectFollowers(id, current, size));
    }

    @Operation(summary = "分页获取关注的用户信息")
    @GetMapping("/v1/users/{id}/followings")
    public Result<IPage<UserPageQueryFollowRespDTO>> selectFollowings(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Results.success(userService.selectFollowings(id, current, size));
    }

    @Operation(summary = "分页搜索用户")
    @GetMapping("/v1/users/search")
    public Result<IPage<UserSearchRespDTO>> searchUsers(UserPageQueryReqDTO requestParam) {
        return Results.success(userService.pageQueryUser(requestParam));
    }
}
