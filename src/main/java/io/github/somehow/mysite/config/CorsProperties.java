package io.github.somehow.mysite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    private String allowedOrigins = "";
    private List<String> allowedOriginsList = new ArrayList<>();

    public List<String> getAllowedOriginsList() {
        if (allowedOriginsList.isEmpty() && allowedOrigins != null && !allowedOrigins.isBlank()) {
            return List.of(allowedOrigins.split(","));
        }
        return allowedOriginsList;
    }
}
