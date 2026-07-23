package io.github.somehow.mysite.ragent.vector;

import java.util.List;

/**
 * 向量存储接口
 *
 * 为什么需要抽象？
 *      当前用 pgvector，但未来如果博客规模变大，可以切换到 Milvus
 *      而不需要改任何业务代码（依赖倒置原则）。
 */
public interface VectorStore {

    /**
     * 插入向量 + 元数据
     *
     * @param vectors   要插入的向量列表
     */
    void insert(List<VectorEntry> vectors);

    /**
     * 向量相似度检索
     *
     * @param queryEmbedding    查询向量
     * @param topK              返回 top K 个最相似的
     * @param kbId              限定知识库（null = 全库检索）。
     *                          现在传 null 即可，但签名先留好 —— 多知识库是近期规划
     *                          届时再加这个参数就是 breaking change 了
     * @return                  检索结果，按相似度降序排列
     */
    List<SearchResult> search(float[] queryEmbedding, int topK, Long kbId);

    /**
     * 删除指定知识库的所有向量
     */
    void deleteByKbId(Long kbId);

    /**
     * 删除指定文档的所有向量
     */
    void deleteByDocId(Long docId);

    // === 内嵌数据类 ===

    record VectorEntry(
            Long chunkId,
            Long kbId,
            float[] embedding,
            String model
    ) {}

    record SearchResult(
            Long chunkId,
            Long docId,
            String docTitle,
            String content,
            float score,    // cosine 相似度，越接近 1 越相似
            Long kbId
    ) {}
}
