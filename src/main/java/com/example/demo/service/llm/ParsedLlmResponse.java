package com.example.demo.service.llm;

import com.example.demo.model.llm.LlmResponseMode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ParsedLlmResponse {

    LlmResponseMode mode;
    String content;
    String finishReason;
    String errorMessage;
}
