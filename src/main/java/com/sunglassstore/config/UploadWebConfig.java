package com.sunglassstore.config;

import com.sunglassstore.service.LocalImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class UploadWebConfig implements WebMvcConfigurer {
    private final LocalImageStorageService storageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = storageService.getUploadRoot().toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations(resourceLocation);
    }
}
