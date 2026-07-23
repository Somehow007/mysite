package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * DeepSeek
 */
@Component
public class DeepseekProvider extends AbstractOpenAiProvider implements LLMProvider {

    public DeepseekProvider(@Value("${rag.llm.providers.deepseek.base-url}") String baseUrl,
                           @Value("${rag.llm.providers.deepseek.api-key}") String apiKey,
                           @Value("${rag.llm.providers.deepseek.chat-model}") String model,
                           @Value("${rag.llm.providers.deepseek.chat-timeout}") Duration timeout,
                           ObjectMapper objectMapper) {
        super(baseUrl, apiKey, model, timeout, objectMapper);
    }

    @Override
    public String getName() {
        return "deepseek";
    }
}
