package io.github.somehow.mysite.controller;

import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.comment.CommentCreateReqDTO;
import io.github.somehow.mysite.dto.resp.CommentLikeRespDTO;
import io.github.somehow.mysite.dto.resp.CommentTreeRespDTO;
import io.github.somehow.mysite.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "评论管理")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "获取文章评论列表")
    @GetMapping("/v1/comments/article/{articleId}")
    public Result<List<CommentTreeRespDTO>> getArticleComments(
            @PathVariable Long articleId,
            HttpServletRequest request) {
        String currentUserId = UserContext.getUserId();
        String currentIp = getClientIp(request);
        return Results.success(commentService.getArticleComments(articleId, currentUserId, currentIp));
    }

    @Operation(summary = "发表评论")
    @PostMapping("/v1/comments")
    public Result<Void> createComment(
            @RequestBody CommentCreateReqDTO requestParam,
            HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        commentService.createComment(requestParam, ipAddress, userAgent);
        return Results.success();
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/v1/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        commentService.deleteComment(id);
        return Results.success();
    }

    @Operation(summary = "点赞/取消点赞评论")
    @PostMapping("/v1/comments/{id}/like")
    public Result<CommentLikeRespDTO> toggleLike(
            @PathVariable Long id,
            HttpServletRequest request) {
        String currentUserId = UserContext.getUserId();
        String ipAddress = getClientIp(request);
        return Results.success(commentService.toggleLike(id, currentUserId, ipAddress));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
