package io.github.somehow.mysite.ragent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMService;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.llm.provider.AbstractOpenAiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 分类/改写专用轻量 LLM 配置。
 *
 * <h3>为什么需要独立的 cheap LLM bean？</h3>
 * 意图分类和查询改写都是"非流式、短输出、高频调用"的场景，
 * 使用 cheap model（如 deepseek-chat）比使用主力生成模型（deepseek-v4-flash）便宜 10×。
 * 这个 bean 不注册为 LLMProvider（不会进入 RoutingLLMService 的降级链），
 * 只是分类器和改写器内部使用。
 */
@Slf4j
@Configuration
public class ClassifierLLMConfig {

    /**
     * 分类/改写专用 LLM —— 使用 cheap model，同步调用。
     * <p>
     * 优先使用 deepseek 的 API（API key 和 base URL 共用，只换 model），
     * 如果 deepseek 未启用则回退到百炼。
     */
    @Bean
    @Qualifier("classificationLLM")
    public LLMService classificationLLM(RagProperties properties, ObjectMapper objectMapper) {
        RagProperties.Provider provider = findBestProvider(properties);
        String cheapModel = resolveCheapModel(provider);
        Duration timeout = provider.getChatTimeout() != null
            ? provider.getChatTimeout()
            : Duration.ofSeconds(30);

        log.info("ClassificationLLM initialized: baseUrl={}, model={}, timeout={}s",
            provider.getBaseUrl(), cheapModel, timeout.getSeconds());

        return new CheapLLMService(provider.getBaseUrl(), provider.getApiKey(),
            cheapModel, timeout, objectMapper);
    }

    /**
     * 从已启用的供应商中选择优先级最高的作为分类 LLM 的后端。
     */
    private RagProperties.Provider findBestProvider(RagProperties properties) {
        return properties.getLlm().getProviders().values().stream()
            .filter(RagProperties.Provider::isEnabled)
            .min(java.util.Comparator.comparingInt(RagProperties.Provider::getPriority))
            .orElseThrow(() -> new IllegalStateException(
                "No enabled LLM provider found for classification"));
    }

    /**
     * 根据供应商类型选择对应的 cheap model 名称。
     */
    private String resolveCheapModel(RagProperties.Provider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl != null && baseUrl.contains("deepseek")) {
            return "deepseek-chat";       // DeepSeek 的便宜模型
        }
        if (baseUrl != null && baseUrl.contains("dashscope")) {
            return "qwen-turbo";          // 百炼的轻量模型
        }
        // 兜底：直接用 provider 配置的 chat-model（不会更贵）
        return provider.getChatModel();
    }

    /**
     * 轻量级 LLM 服务 —— 仅用于非流式分类/改写调用。
     * <p>
     * 直接继承 AbstractOpenAiProvider 以复用 SSE 解析 + 错误处理逻辑，
     * 但不实现 LLMProvider（不会被 RoutingLLMService 收集进去）。
     */
    private static class CheapLLMService extends AbstractOpenAiProvider {
        CheapLLMService(String baseUrl, String apiKey, String model,
                        Duration timeout, ObjectMapper objectMapper) {
            super(baseUrl, apiKey, model, timeout, objectMapper);
        }
    }
}
