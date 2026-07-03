package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import com.example.demo.model.dto.llm.LlmDebugRequest;
import com.example.demo.model.dto.llm.LlmDebugResponse;
import com.example.demo.model.llm.LlmAuthType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpLlmDebugClient {

    private static final String DEFAULT_OPENAI_TEMPLATE = """
            {
              "model": "{{model}}",
              "messages": [
                {
                  "role": "system",
                  "content": "{{systemPrompt}}"
                },
                {
                  "role": "user",
                  "content": "{{message}}"
                }
              ],
              "temperature": "{{params.temperature}}",
              "stream": "{{stream}}"
            }
            """;

    private static final String DEFAULT_OPENAI_MAPPING = """
            {
              "mode": "JSON",
              "contentPath": "choices.0.message.content",
              "finishReasonPath": "choices.0.finish_reason",
              "errorMessagePath": "error.message"
            }
            """;

    private final ObjectMapper objectMapper;
    private final LlmTemplateRenderer llmTemplateRenderer;
    private final LlmResponseExtractor llmResponseExtractor;
    private final LlmSecretCrypto llmSecretCrypto;

    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto) {
        this.objectMapper = objectMapper;
        this.llmTemplateRenderer = llmTemplateRenderer;
        this.llmResponseExtractor = llmResponseExtractor;
        this.llmSecretCrypto = llmSecretCrypto;
    }

    public LlmDebugResponse debug(RuntimeLlmProvider provider, LlmDebugRequest request) {
        validateCapabilities(provider, request);
        long start = System.currentTimeMillis();
        try {
            JsonNode resolvedRequest = llmTemplateRenderer.render(resolveTemplate(provider), buildVariables(provider, request));
            Map<String, String> headers = buildHeaders(provider);
            String body = objectMapper.writeValueAsString(resolvedRequest);

            RestClient restClient = createRestClient(provider);
            ResponseEntity<String> responseEntity = restClient.post()
                    .uri(provider.getEndpointUrl())
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            ParsedLlmResponse parsed = llmResponseExtractor.extract(responseEntity.getBody(), resolveResponseMapping(provider));
            return buildSuccessResponse(
                    responseEntity.getStatusCode().value(),
                    System.currentTimeMillis() - start,
                    body,
                    toJson(headers),
                    responseEntity.getBody(),
                    parsed
            );
        } catch (RestClientResponseException e) {
            return buildErrorResponse(
                    e.getRawStatusCode(),
                    System.currentTimeMillis() - start,
                    mapErrorCode(e.getRawStatusCode()),
                    e.getResponseBodyAsString()
            );
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "请求模板或响应映射格式无效", "json processing failed", e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(503, "LLM 调试调用失败", "llm debug request failed", e);
        }
    }

    public Flux<ServerSentEvent<String>> debugStream(RuntimeLlmProvider provider, LlmDebugRequest request) {
        request.setStream(true);
        LlmDebugResponse response = debug(provider, request);
        List<ServerSentEvent<String>> events = new ArrayList<>();
        for (Map<String, Object> streamEvent : response.getStreamEvents()) {
            events.add(event("chunk", writeJsonSafely(streamEvent)));
        }
        events.add(event("summary", writeJsonSafely(response)));
        events.add(event("done", "{}"));
        return Flux.fromIterable(events);
    }

    private RestClient createRestClient(RuntimeLlmProvider provider) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(provider.getConnectTimeoutMs());
        requestFactory.setReadTimeout(provider.getReadTimeoutMs());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private Map<String, Object> buildVariables(RuntimeLlmProvider provider, LlmDebugRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("model", provider.getDefaultModel());
        variables.put("message", request.getMessage());
        variables.put("systemPrompt", request.getSystemPrompt() == null ? "" : request.getSystemPrompt());
        variables.put("stream", Boolean.TRUE.equals(request.getStream()));
        variables.put("params", mergeParams(provider.getDefaultParamsJson(), request.getParams()));
        variables.put("context", request.getContext() == null ? Map.of() : request.getContext());
        return variables;
    }

    private Map<String, String> buildHeaders(RuntimeLlmProvider provider) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (provider.getDefaultHeadersJson() != null && !provider.getDefaultHeadersJson().isBlank()) {
            try {
                JsonNode defaultHeaders = objectMapper.readTree(provider.getDefaultHeadersJson());
                defaultHeaders.fields().forEachRemaining(entry -> {
                    if (!entry.getValue().isNull()) {
                        headers.put(entry.getKey(), entry.getValue().asText());
                    }
                });
            } catch (JsonProcessingException e) {
                throw new BusinessException(400, "默认请求头 JSON 格式无效");
            }
        }
        if (provider.getAuthType() == LlmAuthType.BEARER) {
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey());
        }
        return headers;
    }

    private String resolveTemplate(RuntimeLlmProvider provider) {
        if (provider.getRequestTemplateJson() != null && !provider.getRequestTemplateJson().isBlank()) {
            return provider.getRequestTemplateJson();
        }
        return DEFAULT_OPENAI_TEMPLATE;
    }

    private String resolveResponseMapping(RuntimeLlmProvider provider) {
        if (provider.getResponseMappingJson() != null && !provider.getResponseMappingJson().isBlank()) {
            return provider.getResponseMappingJson();
        }
        return DEFAULT_OPENAI_MAPPING;
    }

    private LlmDebugResponse buildSuccessResponse(int httpStatus,
                                                  long latencyMs,
                                                  String resolvedRequest,
                                                  String maskedHeadersJson,
                                                  String rawResponse,
                                                  ParsedLlmResponse parsed) {
        LlmDebugResponse response = new LlmDebugResponse();
        response.setSuccess(true);
        response.setHttpStatus(httpStatus);
        response.setLatencyMs(latencyMs);
        response.setResolvedRequestJson(resolvedRequest);
        response.setMaskedHeadersJson(maskedHeadersJson);
        response.setRawResponseText(rawResponse);
        response.setParsedContent(parsed.getContent());
        response.setFinishReason(parsed.getFinishReason());
        response.setStreamEvents(parseStreamEvents(rawResponse));
        return response;
    }

    private LlmDebugResponse buildErrorResponse(int httpStatus,
                                                long latencyMs,
                                                String errorCode,
                                                String rawResponse) {
        LlmDebugResponse response = new LlmDebugResponse();
        response.setSuccess(false);
        response.setHttpStatus(httpStatus);
        response.setLatencyMs(latencyMs);
        response.setErrorCode(errorCode);
        response.setErrorMessage(rawResponse);
        response.setRawResponseText(rawResponse);
        response.setStreamEvents(List.of());
        return response;
    }

    private String toJson(Map<String, String> headers) throws JsonProcessingException {
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key) && value.startsWith("Bearer ")) {
                masked.put(key, "Bearer " + llmSecretCrypto.mask(value.substring("Bearer ".length())));
            } else {
                masked.put(key, value);
            }
        });
        return objectMapper.writeValueAsString(masked);
    }

    private Map<String, Object> mergeParams(String defaultParamsJson, Map<String, Object> requestParams) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (defaultParamsJson != null && !defaultParamsJson.isBlank()) {
            try {
                JsonNode defaults = objectMapper.readTree(defaultParamsJson);
                defaults.fields().forEachRemaining(entry -> merged.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
            } catch (JsonProcessingException e) {
                throw new BusinessException(400, "模型默认参数 JSON 格式无效");
            }
        }
        if (requestParams != null) {
            merged.putAll(requestParams);
        }
        return merged;
    }

    private void validateCapabilities(RuntimeLlmProvider provider, LlmDebugRequest request) {
        JsonNode capabilities = readCapabilities(provider.getCapabilitiesJson());
        if (capabilities.path("chat").isBoolean() && !capabilities.path("chat").asBoolean()) {
            throw new BusinessException(400, "该模型不支持对话调用");
        }
        if (Boolean.TRUE.equals(request.getStream())
                && capabilities.path("stream").isBoolean()
                && !capabilities.path("stream").asBoolean()) {
            throw new BusinessException(400, "该模型不支持流式调试");
        }
    }

    private JsonNode readCapabilities(String capabilitiesJson) {
        if (capabilitiesJson == null || capabilitiesJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(capabilitiesJson);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "模型能力标签 JSON 格式无效");
        }
    }

    private List<Map<String, Object>> parseStreamEvents(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : rawResponse.split("\\R")) {
            if (line == null || line.isBlank() || !line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).trim();
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("payload", payload);
            if (!"[DONE]".equals(payload)) {
                try {
                    event.put("json", objectMapper.readValue(payload, Map.class));
                } catch (IOException ignored) {
                    event.put("text", payload);
                }
            }
            events.add(event);
        }
        return events;
    }

    private ServerSentEvent<String> event(String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data)
                .build();
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private String mapErrorCode(int status) {
        if (status == 401 || status == 403) {
            return "AUTH_ERROR";
        }
        if (status == 408 || status == 504) {
            return "TIMEOUT_ERROR";
        }
        if (status >= 500) {
            return "REMOTE_SERVICE_ERROR";
        }
        return "PROTOCOL_ERROR";
    }
}
