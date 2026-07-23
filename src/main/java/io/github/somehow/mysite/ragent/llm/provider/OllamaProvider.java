package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 本地 Ollama
 */
@Component
public class OllamaProvider extends AbstractOpenAiProvider implements LLMProvider {

    public OllamaProvider(@Value("${rag.llm.providers.ollama.base-url}") String baseUrl,
                           @Value("${rag.llm.providers.ollama.api-key:#{''}}") String apiKey,
                           @Value("${rag.llm.providers.ollama.chat-model}") String model,
                           @Value("${rag.llm.providers.ollama.chat-timeout}") Duration timeout,
                           ObjectMapper objectMapper) {
        super(baseUrl, apiKey, model, timeout, objectMapper);
    }

    @Override
    public String getName() {
        return "ollama";
    }
}
