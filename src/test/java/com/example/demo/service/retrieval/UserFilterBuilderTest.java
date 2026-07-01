package com.example.demo.service.retrieval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class UserFilterBuilderTest {

    @InjectMocks
    private UserFilterBuilder userFilterBuilder;

    @Test
    void build_withNormalUserId_returnsFilterExpression() {
        String result = userFilterBuilder.build("abc123");

        assertEquals("user_id == 'abc123'", result);
    }

    @Test
    void build_withDash_doesNotUseRedisTagEscaping() {
        String result = userFilterBuilder.build("abc-123");

        assertEquals("user_id == 'abc-123'", result);
    }

    @Test
    void build_withDotsAndMixedSpecialChars_keepsMilvusCompatibleLiteral() {
        String result = userFilterBuilder.build("user.name-01");

        assertEquals("user_id == 'user.name-01'", result);
    }

    @Test
    void build_withUnderscore_doesNotEscape() {
        String result = userFilterBuilder.build("user_123");

        assertEquals("user_id == 'user_123'", result);
    }

    @Test
    void build_withNull_returnsEmptyString() {
        assertEquals("", userFilterBuilder.build(null));
    }

    @Test
    void build_withBlank_returnsEmptyString() {
        assertEquals("", userFilterBuilder.build(""));
        assertEquals("", userFilterBuilder.build("   "));
    }

    @Test
    void build_withQuote_escapesStringLiteral() {
        String result = userFilterBuilder.build("user'01");

        assertEquals("user_id == 'user\\'01'", result);
    }
}
