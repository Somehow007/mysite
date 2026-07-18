package io.github.somehow.mysite.ragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * rag 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private DatasourceProperties datasource = new DatasourceProperties();
    private LLMProperties llm = new LLMProperties();
    private ChunkProperties chunk = new ChunkProperties();
    private RetrievalProperties retrieval = new RetrievalProperties();
    private MemoryProperties memory = new MemoryProperties();
    private AsyncProperties async = new AsyncProperties();
    private RateLimitProperties rateLimit = new RateLimitProperties();

    public boolean isProviderEnabled(String name) {
        Provider p = llm.getProviders().get(name);
        return p != null && p.isEnabled();
    }

    public int getProviderPriority(String name) {
        Provider p = llm.getProviders().get(name);
        return p != null ? p.getPriority() : Integer.MAX_VALUE;
    }

    public CircuitBreakerProperties getCircuitBreaker() {
        return llm.getCircuitBreaker();
    }

    @Data
    public static class DatasourceProperties {
        private String driverClassName;
        private String url;
        private String username;
        private String password;
    }

    @Data
    public static class LLMProperties {
        private Map<String, Provider> providers = new HashMap<>();
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    }

    @Data
    public static class Provider {
        private boolean enabled;
        private int priority;
        private String baseUrl;
        private String apiKey;
        private String chatModel;
        private String embeddingModel;
        private String rerankModel;
        private Duration chatTimeout = Duration.ofSeconds(120);
        private Duration embeddingTimeout = Duration.ofSeconds(30);
    }

    @Data
    public static class CircuitBreakerProperties {
        private int failureThreshold = 2;
        private long cooldownSeconds = 30;
    }

    @Data
    public static class ChunkProperties {
        private int size = 800;
        private int overlap = 100;
        private int maxChunksPerDoc = 50;
    }

    @Data
    public static class RetrievalProperties {
        private int topK = 10;
        private int rerankTopK = 5;
        private double scoreThreshold = 0.3;
    }

    @Data
    public static class MemoryProperties {
        private int keepTurns = 6;
        private int summaryTurns = 10;
        private boolean summaryEnabled = false;
    }

    @Data
    public static class AsyncProperties {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 100;
    }

    @Data
    public static class RateLimitProperties {
        private int maxPerHour = 20;
        private int maxQuestionLength = 500;
    }
}
