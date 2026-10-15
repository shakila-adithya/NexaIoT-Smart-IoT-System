package com.nexaiot.server.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String configuredOrigins;

    public CorsConfig(
            @Value("${cors.allowed-origins:http://localhost:5173}") String configuredOrigins
    ) {
        this.configuredOrigins = configuredOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsConfiguration configuration = corsConfiguration();
        registry.addMapping("/api/**")
                .allowedOrigins(configuration.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods(configuration.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(configuration.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(configuration.getExposedHeaders().toArray(String[]::new));
    }

    CorsConfiguration corsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(false);
        return configuration;
    }

    List<String> allowedOrigins() {
        if (configuredOrigins == null) {
            return List.of();
        }
        return Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .filter(origin -> !"*".equals(origin))
                .distinct()
                .toList();
    }
}
