package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.config.RagProperties;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 探测并回写 LLM 非秘密环境变量（模型名等）。API Key 只加密进 PG，不回写本文件。
 * 生产由 {@code start.sh} 读 {@code /opt/mysite/.env}，本地开发读项目根 {@code .env}。
 */
public final class LlmEnvFile {

    public static final Path PRODUCTION_ENV = Path.of("/opt/mysite/.env");

    private LlmEnvFile() {
    }

    public static boolean requiresApiKey(String provider) {
        return provider != null && !"ollama".equalsIgnoreCase(provider);
    }

    public static String apiKeyEnvName(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "deepseek" -> "DEEPSEEK_API_KEY";
            case "bailian" -> "BAILIAN_API_KEY";
            case "siliconflow" -> "SILICONFLOW_API_KEY";
            case "aihubmix" -> "AIHUBMIX_API_KEY";
            default -> null;
        };
    }

    public static String chatModelEnvName(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "deepseek" -> "DEEPSEEK_CHAT_MODEL";
            case "bailian" -> "BAILIAN_CHAT_MODEL";
            case "siliconflow" -> "SILICONFLOW_CHAT_MODEL";
            case "aihubmix" -> "AIHUBMIX_CHAT_MODEL";
            case "ollama" -> "OLLAMA_CHAT_MODEL";
            default -> null;
        };
    }

    public static String embeddingModelEnvName(String provider) {
        if (provider == null || !"bailian".equalsIgnoreCase(provider)) {
            return null;
        }
        return "BAILIAN_EMBEDDING_MODEL";
    }

    public static String embeddingDimensionEnvName(String provider) {
        if (provider == null || !"bailian".equalsIgnoreCase(provider)) {
            return null;
        }
        return "BAILIAN_EMBEDDING_DIMENSION";
    }

    public static String rerankModelEnvName(String provider) {
        if (provider == null || !"bailian".equalsIgnoreCase(provider)) {
            return null;
        }
        return "BAILIAN_RERANK_MODEL";
    }

    public static Path resolve(RagProperties properties) {
        String configured = properties != null ? properties.getLlm().getEnvFile() : null;
        if (StringUtils.hasText(configured)) {
            Path p = Path.of(configured.trim());
            return Files.isRegularFile(p) ? p : null;
        }
        if (Files.isRegularFile(PRODUCTION_ENV)) {
            return PRODUCTION_ENV;
        }
        Path local = Path.of(System.getProperty("user.dir", "."), ".env");
        return Files.isRegularFile(local) ? local : null;
    }

    /**
     * 就地更新已有键，没有则追加。{@code updates} 中值为 {@code null} 的键跳过。
     */
    public static void upsert(Path file, Map<String, String> updates) throws IOException {
        if (file == null || updates == null || updates.isEmpty()) {
            return;
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("env file not found: " + file);
        }
        Map<String, String> pending = new LinkedHashMap<>();
        updates.forEach((k, v) -> {
            if (StringUtils.hasText(k) && v != null) {
                pending.put(k, v);
            }
        });
        if (pending.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String key = trimmed.substring(0, trimmed.indexOf('=')).trim();
            if (pending.containsKey(key)) {
                lines.set(i, key + "=" + pending.remove(key));
            }
        }
        if (!pending.isEmpty()) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add("# LLM (written by admin AI settings)");
            pending.forEach((k, v) -> lines.add(k + "=" + v));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
