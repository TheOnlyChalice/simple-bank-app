package com.simplebank.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the React frontend (Step 4) call this API from the browser.
 * Browsers block requests between different origins (e.g. localhost:5173 -> localhost:8080)
 * unless the API allows them. Only the listed frontend addresses are allowed, not "*".
 * Add the deployed frontend's URL here in Step 5.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",  // Vite (default for new React projects)
                        "http://localhost:3000")  // Create React App
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
