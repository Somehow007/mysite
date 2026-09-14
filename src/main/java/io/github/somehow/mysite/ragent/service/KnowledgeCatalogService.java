package io.github.somehow.mysite.ragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库目录快照 —— 回答「有多少篇 / 谁上传 / 最近入库」等元问题。
 * <p>
 * 作者取 MySQL 文章作者，时间取文章创建时间；不用文档表的 create_time
 * （重建向量会删插重置，且操作 embedding 的人不是作者）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeCatalogService {

    static final int RECENT_LIMIT = 15;
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    /**
     * @param kbIds 用户勾选的知识库；空或 null 则统计全部启用库
     */
    public CatalogSnapshot snapshot(List<Long> kbIds) {
        List<KnowledgeBaseDO> kbs = loadKnowledgeBases(kbIds);
        if (kbs.isEmpty()) {
            return CatalogSnapshot.empty();
        }

        List<KnowledgeDocumentDO> allDocs = new ArrayList<>();
        Map<Long, List<KnowledgeDocumentDO>> docsByKb = new HashMap<>();
        for (KnowledgeBaseDO kb : kbs) {
            List<KnowledgeDocumentDO> docs = docMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentDO>()
                    .eq(KnowledgeDocumentDO::getKbId, kb.getId()));
            docsByKb.put(kb.getId(), docs);
            allDocs.addAll(docs);
        }

        ArticleLookup lookup = loadArticles(allDocs);

        List<KbCatalog> catalogs = new ArrayList<>();
        int totalReady = 0;
        int totalNotReady = 0;
        for (KnowledgeBaseDO kb : kbs) {
            KbCatalog catalog = buildKbCatalog(kb.getName(), docsByKb.getOrDefault(kb.getId(), List.of()), lookup);
            catalogs.add(catalog);
            totalReady += catalog.readyCount();
            totalNotReady += catalog.notReadyCount();
        }
        return new CatalogSnapshot(catalogs, totalReady, totalNotReady);
    }

    private List<KnowledgeBaseDO> loadKnowledgeBases(List<Long> kbIds) {
        if (kbIds != null && !kbIds.isEmpty()) {
            return kbMapper.selectBatchIds(kbIds).stream()
                .filter(Objects::nonNull)
                .toList();
        }
        return kbMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getEnabled, true)
                .or()
                .isNull(KnowledgeBaseDO::getEnabled));
    }

    private ArticleLookup loadArticles(List<KnowledgeDocumentDO> docs) {
        List<Long> articleIds = docs.stream()
            .filter(d -> "ARTICLE".equals(d.getSourceType()))
            .map(KnowledgeDocumentDO::getSourceRef)
            .map(KnowledgeCatalogService::parseLong)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (articleIds.isEmpty()) {
            return ArticleLookup.empty();
        }

        List<ArticleDO> articles = articleMapper.selectBatchIds(articleIds);
        Map<Long, ArticleDO> articleById = new HashMap<>();
        for (ArticleDO article : articles) {
            if (article != null && article.getId() != null) {
                articleById.put(article.getId(), article);
            }
        }

        List<Long> authorIds = articleById.values().stream()
            .map(ArticleDO::getAuthorId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, UserDO> userById = new HashMap<>();
        if (!authorIds.isEmpty()) {
            for (UserDO user : userMapper.selectBatchIds(authorIds)) {
                if (user != null && user.getId() != null) {
                    userById.put(user.getId(), user);
                }
            }
        }
        return new ArticleLookup(articleById, userById);
    }

    private KbCatalog buildKbCatalog(String kbName, List<KnowledgeDocumentDO> docs, ArticleLookup lookup) {
        List<DocStat> ready = new ArrayList<>();
        int notReady = 0;
        for (KnowledgeDocumentDO doc : docs) {
            if (!"READY".equals(doc.getStatus())) {
                notReady++;
                continue;
            }
            ready.add(toDocStat(doc, lookup));
        }
        ready.sort(Comparator.comparing(DocStat::uploadedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, Integer> authorCounts = new LinkedHashMap<>();
        for (DocStat doc : ready) {
            authorCounts.merge(doc.authorName(), 1, Integer::sum);
        }
        List<AuthorStat> authors = authorCounts.entrySet().stream()
            .map(e -> new AuthorStat(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(AuthorStat::articleCount).reversed())
            .toList();

        List<DocStat> recent = ready.size() > RECENT_LIMIT ? ready.subList(0, RECENT_LIMIT) : ready;
        return new KbCatalog(kbName, ready.size(), notReady, authors, List.copyOf(recent));
    }

    private DocStat toDocStat(KnowledgeDocumentDO doc, ArticleLookup lookup) {
        if ("ARTICLE".equals(doc.getSourceType())) {
            Long articleId = parseLong(doc.getSourceRef());
            ArticleDO article = articleId != null ? lookup.articles().get(articleId) : null;
            if (article != null) {
                String author = displayAuthor(lookup.users().get(article.getAuthorId()));
                Instant uploaded = instantOf(article.getCreateTime(), doc.getCreateTime());
                return new DocStat(doc.getTitle(), author, uploaded, doc.getStatus());
            }
        }
        String author = "UPLOAD".equals(doc.getSourceType()) ? "本地上传" : "未知作者";
        Instant uploaded = instantOf(null, doc.getCreateTime());
        return new DocStat(doc.getTitle(), author, uploaded, doc.getStatus());
    }

    static String displayAuthor(UserDO user) {
        if (user == null) {
            return "未知作者";
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return "未知作者";
    }

    static Instant instantOf(Date articleTime, LocalDateTime docTime) {
        if (articleTime != null) {
            return articleTime.toInstant();
        }
        if (docTime != null) {
            return docTime.atZone(ZoneId.systemDefault()).toInstant();
        }
        return Instant.EPOCH;
    }

    static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ArticleLookup(Map<Long, ArticleDO> articles, Map<Long, UserDO> users) {
        static ArticleLookup empty() {
            return new ArticleLookup(Map.of(), Map.of());
        }
    }

    public record CatalogSnapshot(List<KbCatalog> knowledgeBases, int totalReady, int totalNotReady) {
        static CatalogSnapshot empty() {
            return new CatalogSnapshot(List.of(), 0, 0);
        }

        public String toPromptFacts() {
            StringBuilder sb = new StringBuilder();
            if (knowledgeBases.isEmpty()) {
                sb.append("当前没有可统计的知识库。\n");
                return sb.toString();
            }
            sb.append("就绪文章合计 ").append(totalReady).append(" 篇");
            if (totalNotReady > 0) {
                sb.append("（另有 ").append(totalNotReady).append(" 篇未就绪，不含失败原因）");
            }
            sb.append("。\n");
            for (KbCatalog kb : knowledgeBases) {
                sb.append('\n').append("### ").append(kb.name()).append('\n');
                sb.append("- 就绪：").append(kb.readyCount()).append(" 篇");
                if (kb.notReadyCount() > 0) {
                    sb.append("；未就绪：").append(kb.notReadyCount()).append(" 篇");
                }
                sb.append('\n');
                if (kb.authors().isEmpty()) {
                    sb.append("- 作者：无\n");
                } else {
                    sb.append("- 作者：");
                    sb.append(String.join("、", kb.authors().stream()
                        .map(a -> a.authorName() + "（" + a.articleCount() + "）")
                        .toList()));
                    sb.append('\n');
                }
                if (kb.recent().isEmpty()) {
                    sb.append("- 最近文章：无\n");
                } else {
                    DocStat latest = kb.recent().get(0);
                    sb.append("- 最近上传：《").append(latest.title()).append("》，作者 ")
                        .append(latest.authorName()).append("，时间 ")
                        .append(TIME_FMT.format(latest.uploadedAt())).append('\n');
                    sb.append("- 文章列表：\n");
                    for (DocStat doc : kb.recent()) {
                        sb.append("  - 《").append(doc.title()).append("》 ")
                            .append(doc.authorName()).append(' ')
                            .append(TIME_FMT.format(doc.uploadedAt())).append('\n');
                    }
                }
            }
            return sb.toString();
        }
    }

    public record KbCatalog(String name, int readyCount, int notReadyCount,
                            List<AuthorStat> authors, List<DocStat> recent) {}

    public record AuthorStat(String authorName, int articleCount) {}

    public record DocStat(String title, String authorName, Instant uploadedAt, String status) {}
}
