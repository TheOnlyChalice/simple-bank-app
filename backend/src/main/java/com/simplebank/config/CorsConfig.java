package com.simplebank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the React frontend call this API from the browser.
 * Browsers block requests between different origins (e.g. localhost:5173 -> localhost:8080)
 * unless the API allows them. Only the listed frontend addresses are allowed, never "*".
 *
 * The list comes from app.cors.allowed-origins (application.properties). On AWS, the
 * APP_CORS_ALLOWED_ORIGINS environment variable adds the CloudFront address, with no code change.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
