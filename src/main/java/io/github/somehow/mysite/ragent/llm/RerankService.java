package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;

import java.util.List;

/**
 * 重排序服务 —— 用专门的 Rerank 模型对检索结果精排。
 *
 * ⚠️ 格式注意：Rerank 不是 OpenAI 兼容协议的一部分，
 * 各供应商格式不同，不能套用 AbstractOpenAIProvider：
 *
 * 百炼 gte-rerank（本项目选用，走 DashScope 原生接口）：
 *   POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
 *   Header: Authorization: Bearer {apiKey}
 *   Body: {
 *     "model": "gte-rerank",
 *     "input": {
 *       "query": "JWT 过滤器怎么配置？",
 *       "documents": ["文档片段1", "文档片段2", ...]
 *     },
 *     "parameters": {"top_n": 5, "return_documents": true}
 *   }
 *   响应：{"output": {"results": [{"index": 3, "relevance_score": 0.95, "document": {...}}, ...]}}
 *
 * 结论：实现 BaiLianRerankProvider 单独处理百炼格式；rerank 固定使用
 * 主供应商（百炼），不做跨供应商降级 —— 降级供应商没配 rerank 模型时
 * 退化为直接用向量检索的 Top K（RetrievalEngine 里已有这个兜底分支）。
 */
public interface RerankService {

    /**
     * 对候选文档重排序。
     *
     * @param query      用户原始问题
     * @param candidates 向量检索返回的候选列表
     * @param topN       最终返回前 N 条
     * @return 重排序后的结果，按相关性降序，长度 ≤ topN
     */
    List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN);
}
