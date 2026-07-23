package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.llm.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * OpenAI 兼容协议基类 —— 学习价值最高的一个类。
 *
 * 几乎所有国产大模型（百炼、SiliconFlow、DeepSeek 等）都兼容 OpenAI 的 API 格式。
 * 这意味着：学会对接一个，就等于学会对接所有。
 *
 * OpenAI Chat Completions API 格式（关键知识点）：
 *
 * 请求：
 *   POST {baseUrl}/chat/completions
 *   Header: Authorization: Bearer {apiKey}
 *   Body: {
 *     "model": "qwen3-max",
 *     "messages": [
 *       {"role": "system", "content": "你是..."},
 *       {"role": "user", "content": "你好"}
 *     ],
 *     "stream": true,          ← 开启流式输出
 *     "temperature": 0.7
 *   }
 *
 * 响应（stream=true 时，SSE 格式）：
 *   data: {"choices":[{"delta":{"content":"你"},"index":0}]}
 *   data: {"choices":[{"delta":{"content":"好"},"index":0}]}
 *   data: {"choices":[{"delta":{"content":"！"},"index":0}]}
 *   data: {"choices":[{"delta":{},"finish_reason":"stop","index":0}]}
 *   data: [DONE]
 *
 * 响应（stream=false 时，JSON 格式）：
 *   {"choices":[{"message":{"content":"你好！有什么可以帮助你的？"}}]}
 *
 * 当前流行的所有国产模型 API 几乎都遵循这个格式：
 *   - 阿里百炼 DashScope
 *   - SiliconFlow (硅基流动)
 *   - DeepSeek
 *   - 智谱 GLM
 *   - Moonshot (月之暗面)
 *   - 零一万物
 *   - Ollama (本地)
 *   ... 等等
 */
@Slf4j
public abstract class AbstractOpenAiProvider implements LLMService {
    protected final WebClient webClient;
    protected final String apiKey;
    protected final String model;
    protected final Duration timeout;
    protected final ObjectMapper objectMapper;

    public AbstractOpenAiProvider(String baseUrl, String apiKey, String model,
                                  Duration timeout, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        // 使用实际配置的 model（之类可覆盖），而非 request 中的
        ChatRequest actualRequest = ChatRequest.builder()
                .model(this.model)
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .build();

        long t0 = System.currentTimeMillis();
        log.info("[llm] POST /chat/completions model={} msgs={} stream=true timeout={}",
            this.model, actualRequest.getMessages().size(), this.timeout);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", this.model,
                        "messages", actualRequest.getMessages(),
                        "stream", true,
                        "temperature", actualRequest.getTemperature(),
                        "max_tokens", actualRequest.getMaxTokens()
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(this.timeout)
                // 部分供应商（如 Ollama）返回标准 SSE 格式（data: 前缀），
                // 百炼兼容模式返回裸 JSON 行。统一处理：有 data: 前缀则去掉，没有则直传
                .flatMap(chunk -> Flux.fromArray(chunk.split("\n")))
                .filter(line -> !line.isBlank())
                .takeUntil(line -> line.contains("[DONE]"))
                .map(line -> line.startsWith("data: ") ? line.substring(6) : line)
                .map(this::extractDeltaContent)
                .filter(content -> content != null && !content.isEmpty())
                .doOnComplete(() -> log.info("[llm] stream complete for model={}, elapsed={}ms",
                    this.model, System.currentTimeMillis() - t0))
                .doOnError(e -> log.warn("[llm] stream error for model={} after {}ms: {}",
                    this.model, System.currentTimeMillis() - t0, e.getMessage()));
    }

    @Override
    public String chat(ChatRequest request) {
        // 非流式版本：收集所有 token 拼接成完整结果
        return chatStream(request)
                .collectList()
                .map(tokens -> String.join("", tokens))
                .block(timeout);
    }

    /**
     * 从 SSE data 行中提取 delta.content
     * JSON 结构：{"choices": [{"delta":{"content":"你好"},"index":0}]}
     */
    protected String extractDeltaContent(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull()) {
                        return content.asText();
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败静默跳过（可能是非标准格式的 SSE 行）
        }
        return "";
    }
}
