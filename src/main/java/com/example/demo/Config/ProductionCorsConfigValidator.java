package com.example.demo.Config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ProductionCorsConfigValidator {

    private final Environment environment;
    private final CorsProperties corsProperties;

    public ProductionCorsConfigValidator(Environment environment, CorsProperties corsProperties) {
        this.environment = environment;
        this.corsProperties = corsProperties;
    }

    @PostConstruct
    public void validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> origins = corsProperties.getAllowedOrigins() == null
                ? List.of()
                : corsProperties.getAllowedOrigins().stream()
                .map(origin -> origin == null ? "" : origin.trim())
                .filter(origin -> !origin.isBlank())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("生产环境必须显式配置 app.cors.allowed-origins");
        }
        for (String origin : origins) {
            if (isUnsafeProductionOrigin(origin)) {
                throw new IllegalStateException("生产环境 CORS allowed-origins 不能包含开发或通配 Origin: " + origin);
            }
        }
    }

    private boolean isUnsafeProductionOrigin(String origin) {
        String normalized = origin.trim().toLowerCase(Locale.ROOT);
        return "*".equals(normalized)
                || "null".equals(normalized)
                || normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("[::1]")
                || normalized.contains("0.0.0.0");
    }
}
