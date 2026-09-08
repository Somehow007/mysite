package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

@Data
public class LlmProviderPingDTO {
    private boolean ok;
    private String provider;
    private String model;
    private String preview;
    private long latencyMs;
    private String message;
}
