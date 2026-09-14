package io.github.somehow.mysite.ragent.service;

import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeCatalogService — 知识库目录快照")
class KnowledgeCatalogServiceTest {

    private KnowledgeBaseMapper kbMapper;
    private KnowledgeDocumentMapper docMapper;
    private ArticleMapper articleMapper;
    private UserMapper userMapper;
    private KnowledgeCatalogService service;

    @BeforeEach
    void setUp() {
        kbMapper = mock(KnowledgeBaseMapper.class);
        docMapper = mock(KnowledgeDocumentMapper.class);
        articleMapper = mock(ArticleMapper.class);
        userMapper = mock(UserMapper.class);
        service = new KnowledgeCatalogService(kbMapper, docMapper, articleMapper, userMapper);
    }

    @Test
    @DisplayName("ARTICLE 作者来自用户，时间用文章创建时间而不是文档 create_time")
    void articleAuthorAndPublishTimeComeFromMysql() {
        KnowledgeBaseDO kb = kb("默认知识库");
        when(kbMapper.selectList(any())).thenReturn(List.of(kb));

        Date articleTime = Date.from(Instant.parse("2024-03-01T08:00:00Z"));
        LocalDateTime rebuildTime = LocalDateTime.of(2026, 9, 14, 12, 0);

        KnowledgeDocumentDO articleDoc = doc(kb.getId(), "JWT 实战", "ARTICLE", "100", "READY", rebuildTime);
        KnowledgeDocumentDO uploadDoc = doc(kb.getId(), "本地笔记.md", "UPLOAD", "notes.md", "READY", rebuildTime);
        when(docMapper.selectList(any())).thenReturn(List.of(articleDoc, uploadDoc));

        ArticleDO article = new ArticleDO();
        article.setId(100L);
        article.setTitle("JWT 实战");
        article.setAuthorId(7L);
        article.setCreateTime(articleTime);
        when(articleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(article));

        UserDO user = new UserDO();
        user.setId(7L);
        user.setUsername("somehow");
        user.setRealName("张三");
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user));

        KnowledgeCatalogService.CatalogSnapshot snap = service.snapshot(null);
        KnowledgeCatalogService.KbCatalog catalog = snap.knowledgeBases().get(0);

        assertEquals(2, catalog.readyCount());
        KnowledgeCatalogService.DocStat jwt = catalog.recent().stream()
            .filter(d -> "JWT 实战".equals(d.title()))
            .findFirst()
            .orElseThrow();
        assertEquals("张三", jwt.authorName());
        assertEquals(articleTime.toInstant(), jwt.uploadedAt());

        KnowledgeCatalogService.DocStat upload = catalog.recent().stream()
            .filter(d -> "本地笔记.md".equals(d.title()))
            .findFirst()
            .orElseThrow();
        assertEquals("本地上传", upload.authorName());

        String facts = snap.toPromptFacts();
        assertTrue(facts.contains("就绪文章合计 2 篇"));
        assertTrue(facts.contains("张三"));
        assertTrue(facts.contains("本地上传"));
        assertFalse(facts.contains("fail_reason"));
    }

    @Test
    @DisplayName("未填 realName 时用 username")
    void fallsBackToUsername() {
        KnowledgeBaseDO kb = kb("默认知识库");
        when(kbMapper.selectList(any())).thenReturn(List.of(kb));
        when(docMapper.selectList(any())).thenReturn(List.of(
            doc(kb.getId(), "A", "ARTICLE", "1", "READY", LocalDateTime.now())));

        ArticleDO article = new ArticleDO();
        article.setId(1L);
        article.setAuthorId(2L);
        article.setCreateTime(new Date());
        when(articleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(article));

        UserDO user = new UserDO();
        user.setId(2L);
        user.setUsername("alice");
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user));

        KnowledgeCatalogService.DocStat doc = service.snapshot(List.of()).knowledgeBases().get(0).recent().get(0);
        assertEquals("alice", doc.authorName());
    }

    @Test
    @DisplayName("传入 kbIds 时只查这些库，不扫全部启用库")
    void respectsSelectedKbIds() {
        KnowledgeBaseDO kb = kb("指定库");
        when(kbMapper.selectBatchIds(List.of(11L))).thenReturn(List.of(kb));
        when(docMapper.selectList(any())).thenReturn(List.of());

        KnowledgeCatalogService.CatalogSnapshot snap = service.snapshot(List.of(11L));

        assertEquals(1, snap.knowledgeBases().size());
        assertEquals("指定库", snap.knowledgeBases().get(0).name());
        verify(kbMapper).selectBatchIds(List.of(11L));
        verify(kbMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("FAILED 文档不计入就绪，只报告未就绪篇数")
    void failedDocsAreNotReady() {
        KnowledgeBaseDO kb = kb("默认知识库");
        when(kbMapper.selectList(any())).thenReturn(List.of(kb));
        when(docMapper.selectList(any())).thenReturn(List.of(
            doc(kb.getId(), "坏掉的", "ARTICLE", "9", "FAILED", LocalDateTime.now())));

        KnowledgeCatalogService.CatalogSnapshot snap = service.snapshot(null);
        assertEquals(0, snap.totalReady());
        assertEquals(1, snap.totalNotReady());
        assertTrue(snap.toPromptFacts().contains("另有 1 篇未就绪"));
        assertTrue(snap.toPromptFacts().contains("文章列表：无")
            || snap.toPromptFacts().contains("最近文章：无"));
    }

    private static KnowledgeBaseDO kb(String name) {
        KnowledgeBaseDO kb = new KnowledgeBaseDO();
        kb.setId(11L);
        kb.setName(name);
        kb.setEnabled(true);
        return kb;
    }

    private static KnowledgeDocumentDO doc(Long kbId, String title, String sourceType,
                                           String sourceRef, String status, LocalDateTime createTime) {
        KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
        doc.setId(1L);
        doc.setKbId(kbId);
        doc.setTitle(title);
        doc.setSourceType(sourceType);
        doc.setSourceRef(sourceRef);
        doc.setStatus(status);
        doc.setCreateTime(createTime);
        return doc;
    }
}
