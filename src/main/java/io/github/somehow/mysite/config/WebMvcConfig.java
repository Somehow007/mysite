package io.github.somehow.mysite.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ImageUploadConfig imageUploadConfig;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(imageUploadConfig.getBasePath()).toAbsolutePath().toUri().toString();
        registry.addResourceHandler(imageUploadConfig.getUrlPath() + "**")
                .addResourceLocations(absolutePath);
    }
}
