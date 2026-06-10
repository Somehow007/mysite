package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.comment.CommentPageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.CommentAdminRespDTO;
import io.github.somehow.mysite.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "评论管理（管理员）")
@RequestMapping("/v1/admin/comments")
public class AdminCommentController {

    private final CommentService commentService;

    @Operation(summary = "管理员分页查询评论")
    @GetMapping("/list")
    public Result<IPage<CommentAdminRespDTO>> adminPageQuery(CommentPageQueryReqDTO requestParam) {
        return Results.success(commentService.adminPageQuery(requestParam));
    }

    @Operation(summary = "审核评论状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        commentService.updateStatus(id, status);
        return Results.success();
    }
}
