package io.github.somehow.mysite.ragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeChunkMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import io.github.somehow.mysite.ragent.service.KnowledgeDocumentService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v1/rag/knowledge-bases/{kbId}")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentService docService;
    private final ArticleMapper articleMapper;
    private final VectorStore vectorStore;
    private final KnowledgeBaseMapper kbMapper;

    /** 获取知识库内已同步的文档列表（分页 + 筛选） */
    @GetMapping("/docs")
    public Result<IPage<KnowledgeDocumentDO>> listDocs(
            @PathVariable Long kbId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Page<KnowledgeDocumentDO> page = new Page<>(current, size);
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = new LambdaQueryWrapper<KnowledgeDocumentDO>()
                .eq(KnowledgeDocumentDO::getKbId, kbId)
                .orderByDesc(KnowledgeDocumentDO::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(KnowledgeDocumentDO::getTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(KnowledgeDocumentDO::getStatus, status);
        }
        return Results.success(docMapper.selectPage(page, wrapper));
    }

    /** 获取文档的分块列表（分页），用于分块预览 */
    @GetMapping("/docs/{docId}/chunks")
    public Result<IPage<KnowledgeChunkDO>> listChunks(
            @PathVariable Long kbId,
            @PathVariable Long docId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "50") long size) {
        KnowledgeDocumentDO doc = docMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new ClientException("文档不存在");
        }
        Page<KnowledgeChunkDO> page = new Page<>(current, size);
        LambdaQueryWrapper<KnowledgeChunkDO> wrapper = new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, docId)
                .orderByAsc(KnowledgeChunkDO::getChunkIndex);
        return Results.success(chunkMapper.selectPage(page, wrapper));
    }

    /** 获取尚未加入该知识库的博客文章（供选择添加） */
    @GetMapping("/articles/available")
    public Result<List<Map<String, Object>>> availableArticles(@PathVariable Long kbId) {
        // 已同步的文章 ID
        List<KnowledgeDocumentDO> synced = docMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentDO>()
                .eq(KnowledgeDocumentDO::getKbId, kbId)
                .eq(KnowledgeDocumentDO::getSourceType, "ARTICLE"));
        Set<Long> syncedArticleIds = synced.stream()
            .map(d -> Long.valueOf(d.getSourceRef()))
            .collect(Collectors.toSet());

        // 所有文章，排除已同步的
        List<ArticleDO> allArticles = articleMapper.selectList(
            new LambdaQueryWrapper<ArticleDO>()
                .orderByDesc(ArticleDO::getCreateTime));

        List<Map<String, Object>> available = new ArrayList<>();
        for (ArticleDO a : allArticles) {
            if (!syncedArticleIds.contains(a.getId())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", String.valueOf(a.getId()));
                item.put("title", a.getTitle());
                item.put("createTime", a.getCreateTime());
                available.add(item);
            }
        }
        return Results.success(available);
    }

    /** 将指定文章添加到知识库 */
    @PostMapping("/articles")
    public Result<Map<String, Object>> addArticles(@PathVariable Long kbId,
                                                    @RequestBody Map<String, List<Long>> body) {
        KnowledgeBaseDO kb = kbMapper.selectById(kbId);
        if (kb == null) throw new ClientException("知识库不存在");

        List<Long> articleIds = body.getOrDefault("articleIds", List.of());
        if (articleIds.isEmpty()) throw new ClientException("请选择至少一篇文章");

        int added = 0;
        for (Long articleId : articleIds) {
            ArticleDO article = articleMapper.selectById(articleId);
            if (article == null || article.getTitle() == null || article.getContent() == null) continue;
            docService.syncArticle(article, kbId);
            added++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        return Results.success(result);
    }

    /** 删除文档 */
    @DeleteMapping("/docs/{docId}")
    public Result<Void> deleteDoc(@PathVariable Long kbId, @PathVariable Long docId) {
        KnowledgeDocumentDO doc = docMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new ClientException("文档不存在");
        }
        vectorStore.deleteByDocId(docId);
        chunkMapper.deleteByDocId(docId);
        docMapper.deleteById(docId);
        return Results.success();
    }

    /** 重新处理失败的文档 */
    @PostMapping("/docs/{docId}/reprocess")
    public Result<Void> reprocessDoc(@PathVariable Long kbId, @PathVariable Long docId) {
        KnowledgeDocumentDO doc = docMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new ClientException("文档不存在");
        }
        if (!"FAILED".equals(doc.getStatus())) {
            throw new ClientException("只有 FAILED 状态的文档才需要重新处理");
        }
        vectorStore.deleteByDocId(docId);
        chunkMapper.deleteByDocId(docId);
        doc.setStatus("PENDING");
        doc.setFailReason(null);
        docMapper.updateById(doc);

        if ("ARTICLE".equals(doc.getSourceType()) && doc.getSourceRef() != null) {
            ArticleDO article = articleMapper.selectById(Long.valueOf(doc.getSourceRef()));
            if (article != null) {
                docService.syncArticle(article, kbId);
            }
        }
        return Results.success();
    }
}
