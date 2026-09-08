package io.github.somehow.mysite.ragent.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmEnvFile")
class LlmEnvFileTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("就地替换已有键并保留注释")
    void upsertReplacesExistingKeys() throws Exception {
        Path env = tmp.resolve(".env");
        Files.writeString(env, """
                # keep me
                DEEPSEEK_API_KEY=old
                DB_PASSWORD=secret
                """);
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("DEEPSEEK_API_KEY", "new-key");
        updates.put("DEEPSEEK_CHAT_MODEL", "deepseek-chat");
        LlmEnvFile.upsert(env, updates);

        String text = Files.readString(env, StandardCharsets.UTF_8);
        assertTrue(text.contains("# keep me"));
        assertTrue(text.contains("DEEPSEEK_API_KEY=new-key"));
        assertFalse(text.contains("DEEPSEEK_API_KEY=old"));
        assertTrue(text.contains("DB_PASSWORD=secret"));
        assertTrue(text.contains("DEEPSEEK_CHAT_MODEL=deepseek-chat"));
    }

    @Test
    @DisplayName("null 值跳过，不覆盖现有 Key")
    void upsertSkipsNull() throws Exception {
        Path env = tmp.resolve(".env");
        Files.writeString(env, "BAILIAN_API_KEY=keep\n");
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("BAILIAN_API_KEY", null);
        updates.put("BAILIAN_CHAT_MODEL", "qwen-plus");
        LlmEnvFile.upsert(env, updates);
        String text = Files.readString(env);
        assertTrue(text.contains("BAILIAN_API_KEY=keep"));
        assertTrue(text.contains("BAILIAN_CHAT_MODEL=qwen-plus"));
    }
}
