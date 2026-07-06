package com.example.demo.Config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionCorsConfigValidatorTest {

    @Test
    void shouldRequireExplicitCorsOriginsInProduction() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of());
        ProductionCorsConfigValidator validator = new ProductionCorsConfigValidator(prodEnvironment(), properties);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void shouldRejectLocalCorsOriginsInProduction() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:5173"));
        ProductionCorsConfigValidator validator = new ProductionCorsConfigValidator(prodEnvironment(), properties);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void shouldAllowExplicitPublicCorsOriginsInProduction() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://app.example.com"));
        ProductionCorsConfigValidator validator = new ProductionCorsConfigValidator(prodEnvironment(), properties);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void shouldSkipValidationOutsideProduction() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:5173"));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        ProductionCorsConfigValidator validator = new ProductionCorsConfigValidator(environment, properties);

        assertDoesNotThrow(validator::validate);
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
