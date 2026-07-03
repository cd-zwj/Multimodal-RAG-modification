package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import com.example.demo.model.llm.LlmResponseMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LlmJsonConfigValidator {

    private static final Set<String> BOOLEAN_CAPABILITY_KEYS = Set.of(
            "chat", "stream", "jsonMode", "tools", "rag", "longContext"
    );

    private final ObjectMapper objectMapper;

    public LlmJsonConfigValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateProviderConfig(String defaultHeadersJson,
                                       String requestTemplateJson,
                                       String responseMappingJson,
                                       String streamConfigJson) {
        parseObject(defaultHeadersJson, "默认请求头 JSON");
        parseAnyJson(requestTemplateJson, "请求模板 JSON");
        validateResponseMapping(responseMappingJson);
        parseObject(streamConfigJson, "流配置 JSON");
    }

    public void validateModelConfig(String defaultParamsJson, String capabilitiesJson) {
        parseObject(defaultParamsJson, "默认参数 JSON");
        JsonNode capabilities = parseObject(capabilitiesJson, "能力标签 JSON");
        if (capabilities == null) {
            return;
        }
        for (String key : BOOLEAN_CAPABILITY_KEYS) {
            JsonNode value = capabilities.get(key);
            if (value != null && !value.isBoolean()) {
                throw new BusinessException(400, key + " 能力标签必须是 true/false");
            }
        }
    }

    private void validateResponseMapping(String responseMappingJson) {
        JsonNode mapping = parseObject(responseMappingJson, "响应映射 JSON");
        if (mapping == null || !mapping.hasNonNull("mode")) {
            return;
        }
        try {
            LlmResponseMode.valueOf(mapping.get("mode").asText());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "响应映射 mode 仅支持 JSON 或 SSE");
        }
    }

    private JsonNode parseObject(String json, String fieldName) {
        JsonNode node = parseAnyJson(json, fieldName);
        if (node != null && !node.isObject()) {
            throw new BusinessException(400, fieldName + " 必须是 JSON 对象");
        }
        return node;
    }

    private JsonNode parseAnyJson(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, fieldName + " 格式无效");
        }
    }
}
