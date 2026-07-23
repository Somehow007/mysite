package io.github.somehow.mysite.ragent.controller;

import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dto.KnowledgeBaseDTO;
import io.github.somehow.mysite.ragent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/rag/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @GetMapping
    public Result<List<KnowledgeBaseDTO>> list() {
        return Results.success(kbService.listAll());
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseDTO> get(@PathVariable Long id) {
        KnowledgeBaseDTO dto = kbService.getById(id);
        if (dto == null) {
            throw new ClientException("知识库不存在");
        }
        return Results.success(dto);
    }

    @PostMapping
    public Result<KnowledgeBaseDTO> create(@RequestBody KnowledgeBaseDO kb) {
        return Results.success(kbService.create(kb));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseDTO> update(@PathVariable Long id, @RequestBody KnowledgeBaseDO update) {
        KnowledgeBaseDTO dto = kbService.update(id, update);
        if (dto == null) {
            throw new ClientException("知识库不存在");
        }
        return Results.success(dto);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return Results.success();
    }
}
