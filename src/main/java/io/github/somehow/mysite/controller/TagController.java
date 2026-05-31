package io.github.somehow.mysite.controller;

import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.tag.TagCreateReqDTO;
import io.github.somehow.mysite.dto.req.tag.TagUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.tag.TagRespDTO;
import io.github.somehow.mysite.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "标签管理")
public class TagController {

    private final TagService tagService;

    @Operation(summary = "获取所有标签")
    @GetMapping("/v1/tags")
    public Result<List<TagRespDTO>> listTags() {
        return Results.success(tagService.listTags());
    }

    @Operation(summary = "根据slug获取标签详情")
    @GetMapping("/v1/tags/{slug}")
    public Result<TagRespDTO> getTagBySlug(@PathVariable String slug) {
        return Results.success(tagService.getTagBySlug(slug));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/v1/tags")
    public Result<Void> createTag(@Valid @RequestBody TagCreateReqDTO requestParam) {
        tagService.createTag(requestParam);
        return Results.success();
    }

    @Operation(summary = "更新标签")
    @PutMapping("/v1/tags/{id}")
    public Result<Void> updateTag(@PathVariable Long id, @Valid @RequestBody TagUpdateReqDTO requestParam) {
        tagService.updateTag(id, requestParam);
        return Results.success();
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/v1/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Results.success();
    }
}
