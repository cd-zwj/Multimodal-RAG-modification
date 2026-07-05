package com.example.demo.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RagRegressionEvalSetTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evalCasesShouldBeWellFormedAndExecutable() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/rag-regression-eval-cases.json")) {
            assertThat(input).isNotNull();
            List<Map<String, Object>> cases = objectMapper.readValue(input, new TypeReference<>() {});
            assertThat(cases).isNotEmpty();

            Set<String> ids = new HashSet<>();
            for (Map<String, Object> item : cases) {
                assertThat(item.get("id")).as("id").isInstanceOf(String.class);
                assertThat(ids.add((String) item.get("id"))).as("unique id").isTrue();
                assertThat(item.get("question")).as("question").isInstanceOf(String.class);
                assertThat((String) item.get("question")).isNotBlank();
                assertThat(item.get("expectedCitationContains")).as("expectedCitationContains").isInstanceOf(List.class);
                assertThat(item.get("expectedAnswerContains")).as("expectedAnswerContains").isInstanceOf(List.class);
                assertThat((List<?>) item.get("expectedAnswerContains")).isNotEmpty();
            }
        }
    }
}
