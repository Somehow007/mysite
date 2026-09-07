package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

@Data
public class LlmProviderViewDTO {
    private String name;
    private boolean enabled;
    private int priority;
    private String baseUrl;
    private String apiKeyMasked;
    private String chatModel;
    private String embeddingModel;
    private String rerankModel;
    private boolean configured;
}
