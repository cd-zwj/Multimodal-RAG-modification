package com.example.demo.model.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiTurnChatRequestValidationTest {

    private final Validator validator;

    MultiTurnChatRequestValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void shouldRejectBlankSessionIdAndMessage() {
        MultiTurnChatRequest request = new MultiTurnChatRequest();
        request.setSessionId(" ");
        request.setMessage("");

        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> "sessionId".equals(v.getPropertyPath().toString())));
        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> "message".equals(v.getPropertyPath().toString())));
    }

    @Test
    void shouldRejectOversizedMessage() {
        MultiTurnChatRequest request = validRequest();
        request.setMessage("a".repeat(4001));

        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> "message".equals(v.getPropertyPath().toString())));
    }

    private MultiTurnChatRequest validRequest() {
        MultiTurnChatRequest request = new MultiTurnChatRequest();
        request.setSessionId("s1");
        request.setMessage("总结文档");
        request.setTurnCount(1);
        return request;
    }
}
