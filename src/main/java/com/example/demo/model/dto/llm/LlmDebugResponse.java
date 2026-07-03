package com.example.demo.model.dto.llm;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LlmDebugResponse {

    private boolean success;

    private Integer httpStatus;

    private Long latencyMs;

    private String resolvedRequestJson;

    private String maskedHeadersJson;

    private String rawResponseText;

    private String parsedContent;

    private String finishReason;

    private String errorCode;

    private String errorMessage;

    private List<Map<String, Object>> streamEvents;
}
