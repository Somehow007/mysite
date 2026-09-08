package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

@Data
public class LlmProviderUpdateRequest {
    private Boolean enabled;
    private Integer priority;
    private String baseUrl;
    private String chatModel;
    /** 留空表示不改 Key */
    private String apiKey;
}
