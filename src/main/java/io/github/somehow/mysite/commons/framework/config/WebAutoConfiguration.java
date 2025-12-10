package io.github.somehow.mysite.commons.framework.config;

import io.github.somehow.mysite.commons.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Web 组件自动装配
 */
@Configuration
public class WebAutoConfiguration {

    /**
     * 全局异常拦截器
     */
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
