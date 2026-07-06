package com.example.demo.Config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionOpenApiConfigTest {

    @Test
    void shouldDisableOpenApiAndSwaggerUiByDefault() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertEquals("false", properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", properties.getProperty("springdoc.swagger-ui.enabled"));
    }

    @Test
    void shouldDisableOpenApiAndSwaggerUiInProductionProfile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yaml"));
        Properties properties = yaml.getObject();

        assertEquals("false", properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", properties.getProperty("springdoc.swagger-ui.enabled"));
    }
}
