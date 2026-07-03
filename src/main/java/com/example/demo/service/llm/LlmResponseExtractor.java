package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import com.example.demo.model.llm.LlmResponseMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LlmResponseExtractor {

    private final ObjectMapper objectMapper;

    public LlmResponseExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedLlmResponse extract(String response, String mappingJson) {
        try {
            JsonNode mapping = objectMapper.readTree(mappingJson);
            LlmResponseMode mode = LlmResponseMode.valueOf(mapping.path("mode").asText("JSON"));
            return switch (mode) {
                case JSON -> extractJson(response, mapping);
                case SSE -> extractSse(response, mapping);
            };
        } catch (IOException e) {
            throw new BusinessException(400, "响应映射配置无效", "failed to parse response mapping", e);
        }
    }

    private ParsedLlmResponse extractJson(String response, JsonNode mapping) throws IOException {
        JsonNode root = objectMapper.readTree(response);
        return ParsedLlmResponse.builder()
                .mode(LlmResponseMode.JSON)
                .content(readPath(root, mapping.path("contentPath").asText()))
                .finishReason(readPath(root, mapping.path("finishReasonPath").asText()))
                .errorMessage(readPath(root, mapping.path("errorMessagePath").asText()))
                .build();
    }

    private ParsedLlmResponse extractSse(String response, JsonNode mapping) throws IOException {
        String[] lines = response.split("\\R");
        StringBuilder content = new StringBuilder();
        String finishReason = null;
        String doneFlag = mapping.path("streamDoneFlagPath").asText("[DONE]");
        String chunkPath = mapping.path("streamChunkPath").asText();
        String finishReasonPath = mapping.path("finishReasonPath").asText();
        for (String line : lines) {
            if (line == null || line.isBlank() || !line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).trim();
            if (doneFlag.equals(payload)) {
                break;
            }
            JsonNode jsonNode = objectMapper.readTree(payload);
            String chunk = readPath(jsonNode, chunkPath);
            if (chunk != null) {
                content.append(chunk);
            }
            String currentFinish = readPath(jsonNode, finishReasonPath);
            if (currentFinish != null) {
                finishReason = currentFinish;
            }
        }
        return ParsedLlmResponse.builder()
                .mode(LlmResponseMode.SSE)
                .content(content.toString())
                .finishReason(finishReason)
                .build();
    }

    private String readPath(JsonNode root, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            if (current.isArray()) {
                current = current.path(Integer.parseInt(part));
            } else {
                current = current.path(part);
            }
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.isTextual() ? current.asText() : current.toString();
    }
}
