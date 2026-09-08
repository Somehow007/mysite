package io.github.somehow.mysite.ragent.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.ragent.llm.LLMProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AIHubMix —— OpenAI 兼容国际模型代理
 */
@Component
public class AihubmixProvider extends AbstractOpenAiProvider implements LLMProvider {

    public AihubmixProvider(@Value("${rag.llm.providers.aihubmix.base-url}") String baseUrl,
                            @Value("${rag.llm.providers.aihubmix.api-key:#{''}}") String apiKey,
                            @Value("${rag.llm.providers.aihubmix.chat-model}") String model,
                            @Value("${rag.llm.providers.aihubmix.chat-timeout}") Duration timeout,
                            ObjectMapper objectMapper) {
        super(baseUrl, apiKey, model, timeout, objectMapper);
    }

    @Override
    public String getName() {
        return "aihubmix";
    }
}
