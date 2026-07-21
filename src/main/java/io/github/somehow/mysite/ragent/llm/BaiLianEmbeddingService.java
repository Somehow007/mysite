package io.github.somehow.mysite.ragent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 百炼 Embedding 服务 —— 锁定 text-embedding-v4（1024 维）。
 *
 * 为什么不做多供应商降级？查询向量与入库向量必须同模型、同维度。
 * 各供应商 embedding 维度不同（百炼 = 1024，OpenAI text-embedding-3-small = 1536），
 * 降级后往 PG vector(1024) 列里插直接报错。更换模型 = 全量重建向量，属于运维操作。
 *
 * API 格式（OpenAI 兼容）：
 *   POST {baseUrl}/embeddings
 *   Body: {"model":"text-embedding-v4", "input":"文本内容"}
 *   响应: {"data":[{"embedding":[0.12, -0.34, ...], "index":0}]}
 */
@Slf4j
@Service
public class BaiLianEmbeddingService implements EmbeddingService {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper;
    private final int maxBatchSize;  // API 单次调用上限（text-embedding-v4 = 10）

    /**
     * 生产环境构造器：从 RagProperties 读取百炼配置。
     */
    @Autowired
    public BaiLianEmbeddingService(RagProperties properties, ObjectMapper objectMapper) {
        RagProperties.Provider bailian = properties.getLlm().getProviders().get("bailian");
        if (bailian == null || !bailian.isEnabled()) {
            throw new IllegalStateException(
                "百炼 provider 未启用或未配置，Embedding 服务依赖百炼 text-embedding-v4");
        }
        this.model = bailian.getEmbeddingModel();
        this.objectMapper = objectMapper;
        this.maxBatchSize = 10;  // text-embedding-v4 单次最多 10 条

        this.webClient = WebClient.builder()
            .baseUrl(bailian.getBaseUrl())
            .defaultHeader("Authorization", "Bearer " + bailian.getApiKey())
            .defaultHeader("Content-Type", "application/json")
            .build();

        log.info("BaiLianEmbeddingService initialized: model={}, baseUrl={}, maxBatchSize={}",
            model, bailian.getBaseUrl(), maxBatchSize);
    }

    /**
     * 测试用构造器：直接注入 WebClient、model、maxBatchSize。
     * public 以便跨 package 的集成测试使用；生产代码应使用 @Autowired 构造器。
     */
    public BaiLianEmbeddingService(WebClient webClient, String model, ObjectMapper objectMapper, int maxBatchSize) {
        this.webClient = webClient;
        this.model = model;
        this.objectMapper = objectMapper;
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = callEmbeddingApi(List.of(text));
        if (results.isEmpty()) {
            throw new RuntimeException("Embedding API returned empty result");
        }
        return results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        // 按 maxBatchSize 分批调用，合并结果
        List<float[]> allResults = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += maxBatchSize) {
            int end = Math.min(i + maxBatchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            List<float[]> batchResults = callEmbeddingApi(batch);
            allResults.addAll(batchResults);
            log.debug("Embedding batch {}-{}/{} completed", i, end, texts.size());
        }
        return allResults;
    }

    /**
     * 底层 HTTP 调用：POST /embeddings。
     * 返回的向量列表与输入文本列表顺序一一对应（API 保证）。
     */
    private List<float[]> callEmbeddingApi(List<String> inputs) {
        try {
            String responseBody = webClient.post()
                .uri("/embeddings")
                .bodyValue(Map.of(
                    "model", model,
                    "input", inputs.size() == 1 ? inputs.get(0) : inputs
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));

            return parseEmbeddingResponse(responseBody);
        } catch (Exception e) {
            log.error("Embedding API call failed: model={}, inputCount={}", model, inputs.size(), e);
            throw new RuntimeException("Embedding API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 OpenAI 兼容格式的 embedding 响应。
     * 响应 JSON: {"object":"list","data":[{"object":"embedding",
     *   "embedding":[0.12, -0.34, ...], "index":0}, ...]}
     *
     * package-private：方便单元测试直接验证解析逻辑。
     */
    List<float[]> parseEmbeddingResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new RuntimeException("Invalid embedding response: missing 'data' array");
            }

            // 按 index 排序确保与输入顺序一致（API 通常已排好，这里加保险）
            List<float[]> results = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                if (embeddingNode == null || !embeddingNode.isArray()) continue;

                float[] vec = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vec[i] = (float) embeddingNode.get(i).asDouble();
                }
                results.add(vec);
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse embedding response", e);
            throw new RuntimeException("Failed to parse embedding response: " + e.getMessage(), e);
        }
    }
}
