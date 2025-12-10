package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.comment.CommentCreateReqDTO;
import io.github.somehow.mysite.dto.resp.comment.CommentPageQueryRespDTO;
import io.github.somehow.mysite.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论管理层
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "评论管理")
public class CommentController {

    private final CommentService commentService;

    @Operation(description = "创建评论")
    @PostMapping("/api/comment/create")
    public Result<Void> createComment(@RequestBody CommentCreateReqDTO requestParam) {
        commentService.createComment(requestParam);
        return Results.success();
    }

    @Operation(description = "删除评论")
    @DeleteMapping("/api/comment/delete/{id}")
    public Result<Void> deleteComment(@PathVariable(value = "id") String id) {
        commentService.deleteComment(id);
        return Results.success();
    }

    @Operation(description = "分页查询某个文章的所有评论")
    @GetMapping("/api/comment/page/{articleId}")
    public Result<IPage<CommentPageQueryRespDTO>> pageQueryComment(@PathVariable(value = "articleId") String articleId) {
        return Results.success(commentService.pageQueryComments(articleId));
    }
}
