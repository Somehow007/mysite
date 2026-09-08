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
 * <p>
 * 连接参数可 {@link #applyRuntime} 热更新；单次请求在入口拍快照，避免改 Key 时把进行中的流打乱。
 */
@Slf4j
public abstract class AbstractOpenAiProvider implements LLMService {

    private record ClientSnapshot(WebClient webClient, String apiKey, String model,
                                  Duration timeout, String baseUrl) {
    }

    protected final ObjectMapper objectMapper;
    private final AtomicReference<ClientSnapshot> client = new AtomicReference<>();

    public AbstractOpenAiProvider(String baseUrl, String apiKey, String model,
                                  Duration timeout, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        applyRuntime(baseUrl, apiKey, model, timeout);
    }

    public final void applyRuntime(String baseUrl, String apiKey, String model, Duration timeout) {
        Duration t = timeout != null ? timeout : Duration.ofSeconds(120);
        String url = baseUrl != null ? baseUrl : "";
        String key = apiKey != null ? apiKey : "";
        String m = model != null ? model : "";
        WebClient wc = WebClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + key)
                .defaultHeader("Content-Type", "application/json")
                .build();
        client.set(new ClientSnapshot(wc, key, m, t, url));
        log.info("[llm] {} runtime config: model={} baseUrl={}",
                resolveProviderName(), m, url);
    }

    protected WebClient webClient() {
        return requireClient().webClient();
    }

    protected String apiKey() {
        return requireClient().apiKey();
    }

    protected String model() {
        return requireClient().model();
    }

    protected Duration timeout() {
        return requireClient().timeout();
    }

    protected String baseUrl() {
        return requireClient().baseUrl();
    }

    /** @deprecated 使用 {@link #model()}；保留给旧测试子类编译 */
    @Deprecated
    protected String getModel() {
        return model();
    }

    private ClientSnapshot requireClient() {
        ClientSnapshot snap = client.get();
        if (snap == null) {
            throw new IllegalStateException("LLM client not initialized");
        }
        return snap;
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        ClientSnapshot snap = requireClient();
        ChatRequest actualRequest = ChatRequest.builder()
                .model(snap.model())
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
            snap.model(), actualRequest.getMessages() != null ? actualRequest.getMessages().size() : 0, snap.timeout());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", snap.model());
        body.put("messages", actualRequest.getMessages());
        body.put("stream", true);
        body.put("temperature", actualRequest.getTemperature());
        body.put("max_tokens", actualRequest.getMaxTokens());
        body.put("stream_options", Map.of("include_usage", true));

        return snap.webClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(snap.timeout())
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
                        snap.model(), System.currentTimeMillis() - t0);
                    if (recorded.compareAndSet(false, true)) {
                        recordUsage(ctx, providerName, snap.model(), usageRef.get(), estimatedPrompt, output.length(),
                            System.currentTimeMillis() - t0, true, null);
                    }
                })
                .doOnError(e -> {
                    log.warn("[llm] stream error for model={} after {}ms: {}",
                        snap.model(), System.currentTimeMillis() - t0, e.getMessage());
                    if (recorded.compareAndSet(false, true)) {
                        recordUsage(ctx, providerName, snap.model(), usageRef.get(), estimatedPrompt, output.length(),
                            System.currentTimeMillis() - t0, false, e.getMessage());
                    }
                })
                .doOnCancel(() -> {
                    if (recorded.compareAndSet(false, true)) {
                        recordUsage(ctx, providerName, snap.model(), usageRef.get(), estimatedPrompt, output.length(),
                            System.currentTimeMillis() - t0, false, "cancelled");
                    }
                });
    }

    @Override
    public String chat(ChatRequest request) {
        return chatStream(request)
                .collectList()
                .map(tokens -> String.join("", tokens))
                .block(timeout());
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
        return inferProviderName(baseUrl());
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

    private void recordUsage(UsageContext.State ctx, String providerName, String model, TokenUsage apiUsage,
                             int estimatedPrompt, int outputChars, long latencyMs,
                             boolean success, String error) {
        TokenUsage usage = apiUsage;
        if (usage == null) {
            usage = TokenUsage.estimated(estimatedPrompt, TokenUsage.estimateTokens(outputChars));
        }
        LlmUsageRecorder.record(ctx, providerName, model, usage, latencyMs, success, error);
    }
}
