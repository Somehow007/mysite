package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 硅基流动
 */
@Component
public class SiliconFlowProvider extends AbstractOpenAiProvider implements LLMProvider {

    public SiliconFlowProvider(@Value("${rag.llm.providers.siliconflow.base-url}") String baseUrl,
                           @Value("${rag.llm.providers.siliconflow.api-key}") String apiKey,
                           @Value("${rag.llm.providers.siliconflow.chat-model}") String model,
                           @Value("${rag.llm.providers.siliconflow.chat-timeout}") Duration timeout,
                           ObjectMapper objectMapper) {
        super(baseUrl, apiKey, model, timeout, objectMapper);
    }

    @Override
    public String getName() {
        return "siliconflow";
    }
}
