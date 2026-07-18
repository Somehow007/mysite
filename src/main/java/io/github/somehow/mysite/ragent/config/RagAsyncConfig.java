package io.github.somehow.mysite.ragent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * RAG 模块异步线程池配置。
 * 用于文档向量化等耗时操作，不阻塞主线程（如文章发布）。
 */
@Configuration
@EnableAsync
public class RagAsyncConfig {

    @Bean("ragAsyncExecutor")
    public Executor ragAsyncExecutor(RagProperties properties) {
        RagProperties.AsyncProperties async = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("rag-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
