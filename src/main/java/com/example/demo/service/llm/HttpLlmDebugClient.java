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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
    private final LlmOpsMetricsService llmOpsMetricsService;
    private final boolean allowPrivateEndpoints;

    @Autowired
    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto,
                              LlmOpsMetricsService llmOpsMetricsService,
                              @org.springframework.beans.factory.annotation.Value("${llm.allow-private-endpoints:false}") boolean allowPrivateEndpoints) {
        this.objectMapper = objectMapper;
        this.llmTemplateRenderer = llmTemplateRenderer;
        this.llmResponseExtractor = llmResponseExtractor;
        this.llmSecretCrypto = llmSecretCrypto;
        this.llmOpsMetricsService = llmOpsMetricsService;
        this.allowPrivateEndpoints = allowPrivateEndpoints;
    }

    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto) {
        this(objectMapper, llmTemplateRenderer, llmResponseExtractor, llmSecretCrypto, new LlmOpsMetricsService(), false);
    }

    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto,
                              boolean allowPrivateEndpoints) {
        this(objectMapper, llmTemplateRenderer, llmResponseExtractor, llmSecretCrypto, new LlmOpsMetricsService(), allowPrivateEndpoints);
    }

    public LlmDebugResponse debug(RuntimeLlmProvider provider, LlmDebugRequest request) {
        validateCapabilities(provider, request);
        validateEndpointUrl(provider.getEndpointUrl());
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
            LlmDebugResponse response = buildSuccessResponse(
                    responseEntity.getStatusCode().value(),
                    System.currentTimeMillis() - start,
                    body,
                    toJson(headers),
                    responseEntity.getBody(),
                    parsed
            );
            llmOpsMetricsService.recordSuccess(provider, response.getLatencyMs(), parsed.getContent());
            return response;
        } catch (RestClientResponseException e) {
            LlmDebugResponse response = buildErrorResponse(
                    e.getRawStatusCode(),
                    System.currentTimeMillis() - start,
                    mapErrorCode(e.getRawStatusCode()),
                    e.getResponseBodyAsString()
            );
            llmOpsMetricsService.recordFailure(provider, response.getLatencyMs(), response.getErrorCode());
            return response;
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "请求模板或响应映射格式无效", "json processing failed", e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            llmOpsMetricsService.recordFailure(provider, System.currentTimeMillis() - start, e.getMessage());
            throw new BusinessException(503, "LLM 调试调用失败", "llm debug request failed", e);
        }
    }

    public Flux<ServerSentEvent<String>> debugStream(RuntimeLlmProvider provider, LlmDebugRequest request) {
        request.setStream(true);
        long start = System.currentTimeMillis();
        AtomicLong contentChars = new AtomicLong();
        return streamEvents(provider, request)
                .doOnNext(streamEvent -> {
                    Object content = streamEvent.get("content");
                    if (content instanceof String text) {
                        contentChars.addAndGet(text.length());
                    }
                })
                .map(streamEvent -> event("chunk", writeJsonSafely(streamEvent)))
                .concatWithValues(event("done", "{}"))
                .doOnComplete(() -> llmOpsMetricsService.recordSuccess(provider, System.currentTimeMillis() - start, "x".repeat((int) Math.min(contentChars.get(), 10000))))
                .doOnError(error -> llmOpsMetricsService.recordFailure(provider, System.currentTimeMillis() - start, error.getMessage()));
    }

    public Flux<String> streamContent(RuntimeLlmProvider provider, LlmDebugRequest request) {
        request.setStream(true);
        long start = System.currentTimeMillis();
        AtomicLong contentChars = new AtomicLong();
        return streamEvents(provider, request)
                .filter(streamEvent -> !Boolean.TRUE.equals(streamEvent.get("done")))
                .map(streamEvent -> streamEvent.get("content"))
                .filter(String.class::isInstance)
                .cast(String.class)
                .doOnNext(text -> contentChars.addAndGet(text.length()))
                .doOnComplete(() -> llmOpsMetricsService.recordSuccess(provider, System.currentTimeMillis() - start, "x".repeat((int) Math.min(contentChars.get(), 10000))))
                .doOnError(error -> llmOpsMetricsService.recordFailure(provider, System.currentTimeMillis() - start, error.getMessage()));
    }

    private Flux<Map<String, Object>> streamEvents(RuntimeLlmProvider provider, LlmDebugRequest request) {
        validateCapabilities(provider, request);
        validateEndpointUrl(provider.getEndpointUrl());
        return Flux.<Map<String, Object>>create(sink -> {
            try {
                JsonNode resolvedRequest = llmTemplateRenderer.render(resolveTemplate(provider), buildVariables(provider, request));
                Map<String, String> headers = buildHeaders(provider);
                String body = objectMapper.writeValueAsString(resolvedRequest);
                String mappingJson = resolveResponseMapping(provider);
                JsonNode mapping = objectMapper.readTree(mappingJson);
                String doneFlag = mapping.path("streamDoneFlagPath").asText("[DONE]");
                String chunkPath = mapping.path("streamChunkPath").asText();
                String finishReasonPath = mapping.path("finishReasonPath").asText();

                HttpURLConnection connection = openConnection(provider, headers);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int statusCode = connection.getResponseCode();
                if (statusCode < 200 || statusCode >= 300) {
                    sink.error(new BusinessException(statusCode, "自定义模型流式调用失败"));
                    connection.disconnect();
                    return;
                }
                try {
                    consumeSse(connection.getInputStream(), doneFlag, chunkPath, finishReasonPath, sink);
                } finally {
                    connection.disconnect();
                }
                sink.complete();
            } catch (BusinessException e) {
                sink.error(e);
            } catch (Exception e) {
                sink.error(new BusinessException(503, "LLM 流式调用失败", "llm stream request failed", e));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private RestClient createRestClient(RuntimeLlmProvider provider) {
        validateEndpointUrl(provider.getEndpointUrl());
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

    private void validateEndpointUrl(String endpointUrl) {
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "接口地址格式无效", "invalid llm endpoint url", e);
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new BusinessException(400, "接口地址仅支持 HTTP/HTTPS");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(400, "接口地址缺少主机名");
        }
        if (!allowPrivateEndpoints && isPrivateOrLocalHost(host)) {
            throw new BusinessException(400, "接口地址不能指向本机或内网地址");
        }
    }

    private HttpURLConnection openConnection(RuntimeLlmProvider provider, Map<String, String> headers) throws IOException {
        URL url = URI.create(provider.getEndpointUrl()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(provider.getConnectTimeoutMs());
        connection.setReadTimeout(provider.getReadTimeoutMs());
        connection.setDoOutput(true);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.forEach(connection::setRequestProperty);
        return connection;
    }

    private boolean isPrivateOrLocalHost(String host) {
        String normalized = host.trim().toLowerCase();
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(normalized)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            throw new BusinessException(400, "接口地址主机名无法解析", "llm endpoint host cannot be resolved", e);
        }
    }

    private void consumeSse(InputStream inputStream,
                            String doneFlag,
                            String chunkPath,
                            String finishReasonPath,
                            reactor.core.publisher.FluxSink<Map<String, Object>> sink) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder block = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                if (line.isBlank()) {
                    emitSseBlock(block.toString(), doneFlag, chunkPath, finishReasonPath, sink);
                    block.setLength(0);
                    continue;
                }
                block.append(line).append('\n');
            }
            if (!block.isEmpty() && !sink.isCancelled()) {
                emitSseBlock(block.toString(), doneFlag, chunkPath, finishReasonPath, sink);
            }
        }
    }

    private void emitSseBlock(String block,
                              String doneFlag,
                              String chunkPath,
                              String finishReasonPath,
                              reactor.core.publisher.FluxSink<Map<String, Object>> sink) throws IOException {
        if (block == null || block.isBlank()) {
            return;
        }
        String payload = block.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (payload.isBlank()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("payload", payload);
        if (doneFlag.equals(payload)) {
            event.put("done", true);
            sink.next(event);
            return;
        }
        JsonNode jsonNode = objectMapper.readTree(payload);
        event.put("json", objectMapper.convertValue(jsonNode, Map.class));
        String content = readPath(jsonNode, chunkPath);
        if (content != null) {
            event.put("content", content);
        }
        String finishReason = readPath(jsonNode, finishReasonPath);
        if (finishReason != null) {
            event.put("finishReason", finishReason);
        }
        sink.next(event);
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
