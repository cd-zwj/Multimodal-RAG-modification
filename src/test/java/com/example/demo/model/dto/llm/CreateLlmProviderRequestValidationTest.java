package com.example.demo.model.dto.llm;

import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmProtocolType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateLlmProviderRequestValidationTest {

    private final Validator validator;

    CreateLlmProviderRequestValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void shouldRejectBlankProviderCodeAndEndpointUrl() {
        CreateLlmProviderRequest request = validRequest();
        request.setProviderCode(" ");
        request.setEndpointUrl("");

        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> "providerCode".equals(v.getPropertyPath().toString())));
        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> "endpointUrl".equals(v.getPropertyPath().toString())));
    }

    @Test
    void shouldRejectBlankApiKeyAndMissingProtocolType() {
        CreateLlmProviderRequest request = validRequest();
        request.setApiKey(" ");
        request.setProtocolType(null);

        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> "apiKey".equals(v.getPropertyPath().toString())));
        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> "protocolType".equals(v.getPropertyPath().toString())));
    }

    private CreateLlmProviderRequest validRequest() {
        CreateLlmProviderRequest request = new CreateLlmProviderRequest();
        request.setProviderCode("custom-openai");
        request.setProviderName("自定义 OpenAI");
        request.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        request.setAuthType(LlmAuthType.BEARER);
        request.setEndpointUrl("https://example.com/v1/chat/completions");
        request.setApiKey("sk-test");
        request.setDefaultModel("gpt-test");
        request.setConnectTimeoutMs(5000);
        request.setReadTimeoutMs(30000);
        return request;
    }
}
