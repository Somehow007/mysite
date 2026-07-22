package io.github.somehow.mysite.ragent.core.retrieval;

import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.llm.EmbeddingService;
import io.github.somehow.mysite.ragent.llm.RerankService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索引擎（向量检索 + Rerank 精排）
 *
 * 为什么需要两阶段？
 *      Step 1 —— 向量检索（粗排）：从全库中找到 Top 10 候选
 *          - 优点：数独快（HNSW 索引 O(log N)）
 *          - 缺点：纯向量相似度不够精确，可能混入语义相近但不相关的内容
 *      Step 2 —— Rerank（精排）：用专门的 Rerank 模型对 Top 10 重排序
 *          - Rerank 模型比 Embedding 模型更“聪明”：它同时看问题和文档，
 *            判断“这篇文档是否能回答这个问题”，而不仅仅是“这两段文本是否相似”
 *          - 输入：(question，doc1), (question，doc2), ...
 *          - 输出：每个 pair 的相关性分数，取 Top 5
 *          - 成本比直接向量检索高，但只在候选集上跑，所以可控
 *
 * 典型场景：
 *      用户问：“JWT 过滤器怎么配置？”
 *      向量检索可能返回：Top 1 = "JWT 简介"（语义相似但没回答“怎么配置”）
 *      Rerank 会纠正：把 “JWT 过滤器配置步骤” 提到 Top 1
 */
@Component
@RequiredArgsConstructor
public class RetrievalEngine {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final RagProperties properties;

    /**
     * 检索相关文档片段
     *
     * @param question  用户问题（原始文本）
     * @param topK      最终返回多少个片段
     * @return          检索结果，按相关性降序
     */
    public List<VectorStore.SearchResult> retrieve(String question, int topK) {
        // Stage 1: 向量检索 Top K（kbId 传 null = 全库；多知识库时传入目标 kbId）
        float[] queryEmbedding = embeddingService.embed(question);
        List<VectorStore.SearchResult> candidates = vectorStore.search(
                queryEmbedding,
                properties.getRetrieval().getTopK(),
                null
        );

        // 过滤低分结果
        candidates = candidates.stream()
                .filter(r -> r.score() >= properties.getRetrieval().getScoreThreshold())
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        // Stage 2: Rerank 精排（如果配置了 Rerank 服务）
        if (rerankService != null && candidates.size() > topK) {
            candidates = rerankService.rerank(question, candidates, topK);
        } else if (candidates.size() > topK) {
            candidates = candidates.subList(0, topK);
        }
        return candidates;
    }
}
