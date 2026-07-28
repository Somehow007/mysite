package io.github.somehow.mysite.ragent.core;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.llm.embedding.EmbeddingService;
import io.github.somehow.mysite.ragent.llm.rerank.RerankService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 检索引擎（向量检索 + Rerank 精排）。
 *
 * <h3>为什么需要两阶段？</h3>
 * <ol>
 *   <li><b>向量检索（粗排）</b>：从全库中找到 Top K 候选
 *       —— 速度快（HNSW 索引 O(log N)），但可能混入语义相近但不相关的内容</li>
 *   <li><b>Rerank（精排）</b>：用专门的 Rerank 模型对候选集重排序
 *       —— Rerank 模型比 Embedding 模型更"聪明"，同时看问题和文档判断"能否回答"</li>
 * </ol>
 *
 * <h3>Phase 6 改造</h3>
 * <ul>
 *   <li>{@link #retrieve(String, int, List)} —— kbIds 参数支持多 KB 定向检索</li>
 *   <li>{@link #multiRetrieve(List, List, int)} —— 多子问题并行检索 → 去重合并 → Rerank</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalEngine {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final RagProperties properties;

    /**
     * 检索相关文档片段（不限 KB）。
     */
    public List<SearchResult> retrieve(String question, int topK) {
        return retrieve(question, topK, null);
    }

    /**
     * 检索相关文档片段（支持多 KB 定向检索）。
     *
     * @param question 用户问题（原始文本）
     * @param topK     最终返回多少个片段
     * @param kbIds    目标知识库 ID 列表（null 或空 = 全库检索）
     * @return 检索结果，按相关性降序
     */
    public List<SearchResult> retrieve(String question, int topK, List<Long> kbIds) {
        long t0 = System.currentTimeMillis();

        // Stage 1: Embedding + 向量检索
        long t1 = System.currentTimeMillis();
        float[] queryEmbedding = embeddingService.embed(question);
        log.info("[retrieval] embedding done: {} dims ({}ms)",
            queryEmbedding.length, System.currentTimeMillis() - t1);

        List<SearchResult> candidates = vectorStore.search(
            queryEmbedding,
            properties.getRetrieval().getTopK(),
            kbIds
        );
        log.info("[retrieval] vector search: {} candidates, kbIds={} ({}ms total)",
            candidates.size(), kbIds, System.currentTimeMillis() - t0);

        // 过滤低分结果
        candidates = candidates.stream()
            .filter(r -> r.score() >= properties.getRetrieval().getScoreThreshold())
            .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        // Stage 2: Rerank 精排（把用户问题透传给 rerank 模型，按 query↔doc 相关性重排）
        return rerank(question, candidates, topK, t0);
    }

    /**
     * 多子问题检索：每个子问题独立检索 → 去重合并 → Rerank。
     *
     * <h3>使用场景</h3>
     * 查询改写将长问题拆成 2-3 个子问题后，每个子问题分别检索各自最相关的 chunk，
     * 然后去重合并、统一 Rerank。避免长问题 embedding 语义稀释。
     *
     * @param subQueries 子问题列表（通常 2-3 个）
     * @param kbIds      目标知识库 ID 列表（null 或空 = 全库）
     * @param topK       最终返回多少个片段
     * @return 去重合并 + Rerank 后的结果
     */
    public List<SearchResult> multiRetrieve(List<String> subQueries, List<Long> kbIds, int topK) {
        if (subQueries.isEmpty()) return List.of();
        if (subQueries.size() == 1) return retrieve(subQueries.get(0), topK, kbIds);

        long t0 = System.currentTimeMillis();
        log.info("[multi-retrieve] {} sub-queries, kbIds={}", subQueries.size(), kbIds);

        // 每个子问题独立检索（顺序执行 —— embedding API 有并发限制）
        // chunkId → result，用于去重（保留更高分的那个）
        LinkedHashMap<Long, SearchResult> dedupMap = new LinkedHashMap<>();

        for (String subQuery : subQueries) {
            List<SearchResult> results = retrieve(subQuery, topK, kbIds);
            for (SearchResult r : results) {
                SearchResult existing = dedupMap.get(r.chunkId());
                if (existing == null || r.score() > existing.score()) {
                    dedupMap.put(r.chunkId(), r);
                }
            }
        }

        List<SearchResult> merged = new ArrayList<>(dedupMap.values());
        log.info("[multi-retrieve] merged {} unique chunks from {} sub-queries ({}ms)",
            merged.size(), subQueries.size(), System.currentTimeMillis() - t0);

        if (merged.isEmpty()) return List.of();

        // 按分数降序 → 取 topK → Rerank
        merged.sort((a, b) -> Float.compare(b.score(), a.score()));
        if (merged.size() > properties.getRetrieval().getTopK()) {
            merged = merged.subList(0, properties.getRetrieval().getTopK());
        }

        // 多子问题合并后的统一精排：用全部子问题拼接作为 rerank query，
        // 让 rerank 模型看到完整的问题语境（而非空串）
        return rerank(String.join("\n", subQueries), merged, topK, t0);
    }

    // ── private helpers ──

    /**
     * Rerank 精排 + 精排后二次阈值过滤。
     *
     * @param query      用户问题（透传给 rerank 模型做 query↔doc 相关性打分，不能为空）
     * @param candidates 粗排候选（已通过向量 scoreThreshold）
     * @param topK       最终返回数量上限
     */
    private List<SearchResult> rerank(String query, List<SearchResult> candidates,
                                      int topK, long startTime) {
        if (rerankService != null && candidates.size() > topK) {
            long tr = System.currentTimeMillis();
            candidates = rerankService.rerank(query, candidates, topK);
            log.info("[retrieval] rerank done: {} results ({}ms)",
                candidates.size(), System.currentTimeMillis() - tr);
        } else if (candidates.size() > topK) {
            candidates = candidates.subList(0, topK);
        }

        // 精排后二次过滤：rerank 的 relevance_score 会替换向量分，
        // 必须按精排阈值再卡一道，否则低相关性来源（如 34%）也会混进 prompt。
        // 降级路径（无 rerank）下分数仍是向量相似度、已过粗排阈值，此过滤天然兼容。
        double rerankThreshold = properties.getRetrieval().getRerankScoreThreshold();
        int before = candidates.size();
        candidates = candidates.stream()
            .filter(r -> r.score() >= rerankThreshold)
            .toList();
        if (candidates.size() < before) {
            log.info("[retrieval] post-rerank filter (threshold={}): {} → {} results",
                rerankThreshold, before, candidates.size());
        }

        log.info("[retrieval] done: {} final results, total={}ms",
            candidates.size(), System.currentTimeMillis() - startTime);
        return candidates;
    }
}
