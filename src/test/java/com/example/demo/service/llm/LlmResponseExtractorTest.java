package com.example.demo.service.llm;

import com.example.demo.model.llm.LlmResponseMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmResponseExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LlmResponseExtractor extractor = new LlmResponseExtractor(MAPPER);

    @Test
    void shouldExtractJsonResponseContent() {
        String mapping = """
                {
                  "mode": "JSON",
                  "contentPath": "choices.0.message.content",
                  "finishReasonPath": "choices.0.finish_reason",
                  "errorMessagePath": "error.message"
                }
                """;
        String response = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "测试成功"
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;

        ParsedLlmResponse parsed = extractor.extract(response, mapping);

        assertEquals(LlmResponseMode.JSON, parsed.getMode());
        assertEquals("测试成功", parsed.getContent());
        assertEquals("stop", parsed.getFinishReason());
    }

    @Test
    void shouldAggregateSseChunksIntoFinalContent() {
        String mapping = """
                {
                  "mode": "SSE",
                  "streamChunkPath": "choices.0.delta.content",
                  "finishReasonPath": "choices.0.finish_reason",
                  "streamDoneFlagPath": "[DONE]"
                }
                """;
        String response = """
                data: {"choices":[{"delta":{"content":"你"}}]}

                data: {"choices":[{"delta":{"content":"好"}}]}

                data: {"choices":[{"finish_reason":"stop"}]}

                data: [DONE]
                """;

        ParsedLlmResponse parsed = extractor.extract(response, mapping);

        assertEquals(LlmResponseMode.SSE, parsed.getMode());
        assertEquals("你好", parsed.getContent());
        assertEquals("stop", parsed.getFinishReason());
    }
}
