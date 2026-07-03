package com.example.demo.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmTemplateRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LlmTemplateRenderer renderer = new LlmTemplateRenderer(MAPPER);

    @Test
    void shouldRenderTypedJsonValuesFromTemplate() throws Exception {
        String template = """
                {
                  "model": "{{model}}",
                  "stream": "{{stream}}",
                  "temperature": "{{params.temperature}}",
                  "messages": [
                    {
                      "role": "system",
                      "content": "{{systemPrompt}}"
                    },
                    {
                      "role": "user",
                      "content": "{{message}}"
                    }
                  ]
                }
                """;

        Map<String, Object> variables = Map.of(
                "model", "qwen-plus",
                "stream", true,
                "systemPrompt", "你是一个助手",
                "message", "你好",
                "params", Map.of("temperature", 0.7)
        );

        JsonNode rendered = renderer.render(template, variables);

        assertEquals("qwen-plus", rendered.get("model").asText());
        assertTrue(rendered.get("stream").asBoolean());
        assertEquals(0.7d, rendered.get("temperature").asDouble(), 0.0001d);
        assertEquals("你好", rendered.get("messages").get(1).get("content").asText());
    }

    @Test
    void shouldReplaceEmbeddedPlaceholdersInsideText() throws Exception {
        String template = """
                {
                  "prompt": "用户 {{context.userId}} 提问：{{message}}"
                }
                """;

        Map<String, Object> variables = Map.of(
                "message", "总结一下",
                "context", Map.of("userId", "u-1")
        );

        JsonNode rendered = renderer.render(template, variables);

        assertEquals("用户 u-1 提问：总结一下", rendered.get("prompt").asText());
    }
}
