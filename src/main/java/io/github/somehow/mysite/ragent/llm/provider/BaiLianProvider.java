package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 阿里百炼
 */
@Component
public class BaiLianProvider extends AbstractOpenAiProvider implements LLMProvider {

    public BaiLianProvider(@Value("${rag.llm.providers.bailian.base-url}") String baseUrl,
                           @Value("${rag.llm.providers.bailian.api-key}") String apiKey,
                           @Value("${rag.llm.providers.bailian.chat-model}") String model,
                           @Value("${rag.llm.providers.bailian.chat-timeout}") Duration timeout,
                           ObjectMapper objectMapper) {
        super(baseUrl, apiKey, model, timeout, objectMapper);
    }

    @Override
    public String getName() {
        return "bailian";
    }
}
