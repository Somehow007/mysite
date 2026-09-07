package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMProvider;
import io.github.somehow.mysite.ragent.llm.LLMService;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.usage.LlmUsageRecorder;
import io.github.somehow.mysite.ragent.usage.TokenUsage;
import io.github.somehow.mysite.ragent.usage.UsageContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI 兼容协议基类。流式请求开启 {@code stream_options.include_usage}，
 * 从末包解析 token 用量；拿不到则按字符估算。记录走 {@link LlmUsageRecorder}，不进入 SSE。
 */
@Slf4j
public abstract class AbstractOpenAiProvider implements LLMService {
    protected final WebClient webClient;
    protected final String apiKey;
    protected final String model;
    protected final Duration timeout;
    protected final ObjectMapper objectMapper;
    protected final String baseUrl;

    public AbstractOpenAiProvider(String baseUrl, String apiKey, String model,
                                  Duration timeout, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
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
        ChatRequest actualRequest = ChatRequest.builder()
                .model(this.model)
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .build();

        UsageContext.State ctx = UsageContext.snapshot();
        String providerName = resolveProviderName();
        int estimatedPrompt = estimatePromptTokens(actualRequest);
        AtomicReference<TokenUsage> usageRef = new AtomicReference<>();
        StringBuilder output = new StringBuilder();
        long t0 = System.currentTimeMillis();
        AtomicBoolean recorded = new AtomicBoolean(false);

        log.info("[llm] POST /chat/completions model={} msgs={} stream=true timeout={}",
            this.model, actualRequest.getMessages() != null ? actualRequest.getMessages().size() : 0, this.timeout);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", this.model);
        body.put("messages", actualRequest.getMessages());
        body.put("stream", true);
        body.put("temperature", actualRequest.getTemperature());
        body.put("max_tokens", actualRequest.getMaxTokens());
        body.put("stream_options", Map.of("include_usage", true));

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(this.timeout)
                .flatMap(chunk -> Flux.fromArray(chunk.split("\n")))
                .filter(line -> !line.isBlank())
                .takeUntil(line -> line.contains("[DONE]"))
                .map(line -> line.startsWith("data: ") ? line.substring(6) : line)
                .filter(json -> json != null && !json.isBlank() && !json.contains("[DONE]"))
                .<String>handle((json, sink) -> {
                    TokenUsage parsed = extractUsage(json);
                    if (parsed != null) {
                        usageRef.set(parsed);
                    }
                    String content = extractDeltaContent(json);
                    if (content != null && !content.isEmpty()) {
                        output.append(content);
                        sink.next(content);
                    }
                })
                .doOnComplete(() -> {
                    log.info("[llm] stream complete for model={}, elapsed={}ms",
                        this.model, System.currentTimeMillis() - t0);
                    if (recorded.compareAndSet(false, true)) {
                        recordUsage(ctx, providerName, usageRef.get(), estimatedPrompt, output.length(),
                            System.currentTimeMillis() - t0, true, null);
                    }
                })
                .doOnError(e -> {
                    log.warn("[llm] stream error for model={} after {}ms: {}",
                        this.model, System.currentTimeMillis() - t0, e.getMessage());
                    if (recorded.compareAndSet(false, true)) {
                        recordUsage(ctx, providerName, usageRef.get(), estimatedPrompt, output.length(),
                            System.currentTimeMillis() - t0, false, e.getMessage());
                    }
                })
                .doOnCancel(() -> {
                    if (recorded.compareAndSet(false, true)) {
                        recordUsage(ctx, providerName, usageRef.get(), estimatedPrompt, output.length(),
                            System.currentTimeMillis() - t0, false, "cancelled");
                    }
                });
    }

    @Override
    public String chat(ChatRequest request) {
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

    protected TokenUsage extractUsage(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode usage = root.get("usage");
            if (usage == null || usage.isNull()) {
                return null;
            }
            int prompt = usage.path("prompt_tokens").asInt(0);
            int completion = usage.path("completion_tokens").asInt(0);
            int total = usage.path("total_tokens").asInt(0);
            if (prompt == 0 && completion == 0 && total == 0) {
                return null;
            }
            return TokenUsage.api(prompt, completion, total);
        } catch (Exception e) {
            return null;
        }
    }

    protected String resolveProviderName() {
        if (this instanceof LLMProvider p) {
            return p.getName();
        }
        return inferProviderName(baseUrl);
    }

    static String inferProviderName(String url) {
        if (url == null) {
            return "unknown";
        }
        String u = url.toLowerCase();
        if (u.contains("deepseek")) return "deepseek";
        if (u.contains("dashscope")) return "bailian";
        if (u.contains("siliconflow")) return "siliconflow";
        if (u.contains("aihubmix")) return "aihubmix";
        if (u.contains("11434") || u.contains("ollama")) return "ollama";
        return "unknown";
    }

    private static int estimatePromptTokens(ChatRequest request) {
        if (request.getMessages() == null) {
            return 0;
        }
        int chars = 0;
        for (ChatMessage msg : request.getMessages()) {
            if (msg.getContent() != null) {
                chars += msg.getContent().length();
            }
        }
        return TokenUsage.estimateTokens(chars);
    }

    private void recordUsage(UsageContext.State ctx, String providerName, TokenUsage apiUsage,
                             int estimatedPrompt, int outputChars, long latencyMs,
                             boolean success, String error) {
        TokenUsage usage = apiUsage;
        if (usage == null) {
            usage = TokenUsage.estimated(estimatedPrompt, TokenUsage.estimateTokens(outputChars));
        }
        LlmUsageRecorder.record(ctx, providerName, this.model, usage, latencyMs, success, error);
    }
}
