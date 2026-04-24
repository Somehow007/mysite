package io.github.somehow.mysite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    private boolean enabled = true;

    private String[] uris = {"http://localhost:9200"};

    private int connectionTimeout = 5000;

    private int socketTimeout = 30000;
}
