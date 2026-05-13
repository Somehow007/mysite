package io.github.somehow.mysite.commons.framework.config;

import io.github.somehow.mysite.commons.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebAutoConfiguration {

    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
