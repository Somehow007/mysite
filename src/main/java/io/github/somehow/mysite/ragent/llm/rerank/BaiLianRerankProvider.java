package io.github.somehow.mysite.ragent.llm.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.vector.VectorStore.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 百炼 Rerank 服务 —— DashScope 原生 rerank API（非 OpenAI 兼容协议）。
 *
 * API 协议（gte-rerank）：
 *   POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
 *   Header: Authorization: Bearer {apiKey}
 *   Body: {
 *     "model": "gte-rerank",
 *     "input": { "query": "...", "documents": ["片段1", "片段2", ...] },
 *     "parameters": { "top_n": 5, "return_documents": false }
 *   }
 *   响应: { "output": { "results": [{"index": 3, "relevance_score": 0.95}, ...] } }
 *
 * 如果供应商未启用或未配置 rerank 模型，此 Bean 不会被创建
 * （由 RagChatService / RetrievalEngine 自行处理 null 降级）。
 */
@Slf4j
@Component
public class BaiLianRerankProvider implements RerankService {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public BaiLianRerankProvider(RagProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        RagProperties.Provider bailian = properties.getLlm().getProviders().get("bailian");

        if (bailian == null || !bailian.isEnabled() || bailian.getRerankModel() == null) {
            // 百炼未启用或未配 rerank 模型 → 不初始化客户端，rerank() 退化为截断
            this.webClient = null;
            this.model = null;
            log.info("BaiLianRerankProvider disabled: bailian not configured for rerank");
        } else {
            this.model = bailian.getRerankModel();
            this.webClient = WebClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .defaultHeader("Authorization", "Bearer " + bailian.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
            log.info("BaiLianRerankProvider initialized: model={}", model);
        }
    }

    @Override
    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topN) {
        if (webClient == null || model == null || candidates.isEmpty()) {
            return truncate(candidates, topN);
        }

        if (candidates.size() <= topN) {
            return new ArrayList<>(candidates);
        }

        try {
            return callRerankApi(query, candidates, topN);
        } catch (Exception e) {
            log.warn("Rerank API call failed, falling back to vector truncation: {}", e.getMessage());
            return truncate(candidates, topN);
        }
    }

    private List<SearchResult> callRerankApi(String query, List<SearchResult> candidates, int topN) {
        List<String> documents = candidates.stream()
            .map(SearchResult::content)
            .toList();

        Map<String, Object> body = Map.of(
            "model", model,
            "input", Map.of("query", query, "documents", documents),
            "parameters", Map.of("top_n", topN, "return_documents", false)
        );

        long t0 = System.currentTimeMillis();
        log.info("[rerank] calling {} with {} candidates, topN={}", model, documents.size(), topN);

        String responseBody;
        try {
            responseBody = webClient.post()
                .uri("/api/v1/services/rerank/text-rerank/text-rerank")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
            log.info("[rerank] API call done: responseLen={}, elapsed={}ms",
                responseBody != null ? responseBody.length() : 0, System.currentTimeMillis() - t0);
        } catch (WebClientResponseException e) {
            log.warn("[rerank] HTTP {} after {}ms: {}",
                e.getStatusCode(), System.currentTimeMillis() - t0, e.getResponseBodyAsString());
            throw new RuntimeException("Rerank HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }

        return parseRerankResponse(responseBody, candidates, topN);
    }

    /**
     * 解析 DashScope rerank 响应，按 index 将 relevance_score 映射回 SearchResult。
     */
    private List<SearchResult> parseRerankResponse(String responseBody,
                                                    List<SearchResult> candidates,
                                                    int topN) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("output").path("results");
            if (!results.isArray()) {
                log.warn("Rerank response missing output.results array, falling back");
                return truncate(candidates, topN);
            }

            // 按 index 映射 score，然后用新分数重建 SearchResult
            record IndexedScore(int index, float score) {}
            List<IndexedScore> scores = new ArrayList<>();
            for (JsonNode item : results) {
                int idx = item.path("index").asInt();
                float score = (float) item.path("relevance_score").asDouble();
                scores.add(new IndexedScore(idx, score));
            }

            return scores.stream()
                .filter(is -> is.index >= 0 && is.index < candidates.size())
                .map(is -> {
                    SearchResult orig = candidates.get(is.index);
                    return new SearchResult(orig.chunkId(), orig.docId(),
                        orig.docTitle(), orig.content(), is.score, orig.kbId());
                })
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topN)
                .toList();

        } catch (Exception e) {
            log.warn("Failed to parse rerank response, falling back: {}", e.getMessage());
            return truncate(candidates, topN);
        }
    }

    private List<SearchResult> truncate(List<SearchResult> candidates, int topN) {
        if (candidates.size() <= topN) {
            return new ArrayList<>(candidates);
        }
        return new ArrayList<>(candidates.subList(0, topN));
    }
}
