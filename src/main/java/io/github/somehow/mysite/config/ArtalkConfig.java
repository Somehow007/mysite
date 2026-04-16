package io.github.somehow.mysite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "artalk")
public class ArtalkConfig {

    private String server;

    private String site;

    private Boolean enabled = true;
}
