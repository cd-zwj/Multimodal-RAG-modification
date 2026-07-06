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
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final Set<Integer> ALLOWED_PUBLIC_PORTS = Set.of(80, 443, 8443);
    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
            "apikey",
            "openai-organization",
            "openai-project",
            "anthropic-api-key",
            "x-goog-api-key",
            "x-auth-token"
    );
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_SSE_EVENTS = 1000;
    private static final long DEFAULT_MAX_STREAM_DURATION_MS = 120_000L;
    private static final int DEFAULT_MAX_ACTIVE_STREAMS = 50;

    private final ObjectMapper objectMapper;
    private final LlmTemplateRenderer llmTemplateRenderer;
    private final LlmResponseExtractor llmResponseExtractor;
    private final LlmSecretCrypto llmSecretCrypto;
    private final LlmOpsMetricsService llmOpsMetricsService;
    private final boolean allowPrivateEndpoints;
    private final int maxResponseBytes;
    private final int maxSseEvents;
    private final long maxStreamDurationMs;
    private final Semaphore activeStreams;

    @Autowired
    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto,
                              LlmOpsMetricsService llmOpsMetricsService,
                              @org.springframework.beans.factory.annotation.Value("${llm.allow-private-endpoints:false}") boolean allowPrivateEndpoints,
                              @org.springframework.beans.factory.annotation.Value("${llm.debug.max-response-bytes:1048576}") int maxResponseBytes,
                              @org.springframework.beans.factory.annotation.Value("${llm.debug.max-sse-events:1000}") int maxSseEvents,
                              @org.springframework.beans.factory.annotation.Value("${llm.debug.max-stream-duration-ms:120000}") long maxStreamDurationMs,
                              @org.springframework.beans.factory.annotation.Value("${llm.debug.max-active-streams:50}") int maxActiveStreams) {
        this.objectMapper = objectMapper;
        this.llmTemplateRenderer = llmTemplateRenderer;
        this.llmResponseExtractor = llmResponseExtractor;
        this.llmSecretCrypto = llmSecretCrypto;
        this.llmOpsMetricsService = llmOpsMetricsService;
        this.allowPrivateEndpoints = allowPrivateEndpoints;
        this.maxResponseBytes = Math.max(1, maxResponseBytes);
        this.maxSseEvents = Math.max(1, maxSseEvents);
        this.maxStreamDurationMs = Math.max(1L, maxStreamDurationMs);
        this.activeStreams = new Semaphore(Math.max(0, maxActiveStreams));
    }

    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto) {
        this(objectMapper, llmTemplateRenderer, llmResponseExtractor, llmSecretCrypto, new LlmOpsMetricsService(), false, DEFAULT_MAX_RESPONSE_BYTES, DEFAULT_MAX_SSE_EVENTS, DEFAULT_MAX_STREAM_DURATION_MS, DEFAULT_MAX_ACTIVE_STREAMS);
    }

    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto,
                              boolean allowPrivateEndpoints) {
        this(objectMapper, llmTemplateRenderer, llmResponseExtractor, llmSecretCrypto, new LlmOpsMetricsService(), allowPrivateEndpoints, DEFAULT_MAX_RESPONSE_BYTES, DEFAULT_MAX_SSE_EVENTS, DEFAULT_MAX_STREAM_DURATION_MS, DEFAULT_MAX_ACTIVE_STREAMS);
    }

    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto,
                              boolean allowPrivateEndpoints,
                              int maxResponseBytes,
                              int maxSseEvents) {
        this(objectMapper, llmTemplateRenderer, llmResponseExtractor, llmSecretCrypto, new LlmOpsMetricsService(), allowPrivateEndpoints, maxResponseBytes, maxSseEvents, DEFAULT_MAX_STREAM_DURATION_MS, DEFAULT_MAX_ACTIVE_STREAMS);
    }

    public HttpLlmDebugClient(ObjectMapper objectMapper,
                              LlmTemplateRenderer llmTemplateRenderer,
                              LlmResponseExtractor llmResponseExtractor,
                              LlmSecretCrypto llmSecretCrypto,
                              boolean allowPrivateEndpoints,
                              int maxResponseBytes,
                              int maxSseEvents,
                              long maxStreamDurationMs,
                              int maxActiveStreams) {
        this(objectMapper, llmTemplateRenderer, llmResponseExtractor, llmSecretCrypto, new LlmOpsMetricsService(), allowPrivateEndpoints, maxResponseBytes, maxSseEvents, maxStreamDurationMs, maxActiveStreams);
    }

    public LlmDebugResponse debug(RuntimeLlmProvider provider, LlmDebugRequest request) {
        validateCapabilities(provider, request);
        ValidatedEndpoint endpoint = validateEndpointUrl(provider.getEndpointUrl());
        long start = System.currentTimeMillis();
        try {
            JsonNode resolvedRequest = llmTemplateRenderer.render(resolveTemplate(provider), buildVariables(provider, request));
            Map<String, String> headers = buildHeaders(provider);
            String body = objectMapper.writeValueAsString(resolvedRequest);

            HttpURLConnection connection = openConnection(provider, headers, endpoint);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int statusCode = connection.getResponseCode();
            verifyEndpointStillPinned(endpoint);
            String rawResponse;
            try {
                rawResponse = readResponseBody(connection, statusCode);
            } finally {
                connection.disconnect();
            }
            if (statusCode < 200 || statusCode >= 300) {
                LlmDebugResponse response = buildErrorResponse(
                        statusCode,
                        System.currentTimeMillis() - start,
                        mapErrorCode(statusCode),
                        rawResponse
                );
                llmOpsMetricsService.recordFailure(provider, response.getLatencyMs(), response.getErrorCode());
                return response;
            }

            ParsedLlmResponse parsed = llmResponseExtractor.extract(rawResponse, resolveResponseMapping(provider));
            LlmDebugResponse response = buildSuccessResponse(
                    statusCode,
                    System.currentTimeMillis() - start,
                    body,
                    toJson(headers),
                    rawResponse,
                    parsed
            );
            llmOpsMetricsService.recordSuccess(provider, response.getLatencyMs(), parsed.getContent());
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
        ValidatedEndpoint endpoint = validateEndpointUrl(provider.getEndpointUrl());
        return Flux.<Map<String, Object>>create(sink -> {
            if (!activeStreams.tryAcquire()) {
                sink.error(new BusinessException(429, "LLM 流式调试并发过高，请稍后再试"));
                return;
            }
            AtomicBoolean released = new AtomicBoolean(false);
            AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
            Disposable deadlineTask = Schedulers.boundedElastic().schedule(
                    () -> {
                        HttpURLConnection connection = connectionRef.get();
                        if (connection != null) {
                            connection.disconnect();
                        }
                        if (!sink.isCancelled()) {
                            sink.error(new BusinessException(408, "LLM 流式响应超时"));
                        }
                    },
                    maxStreamDurationMs,
                    TimeUnit.MILLISECONDS
            );
            sink.onCancel(() -> {
                deadlineTask.dispose();
                HttpURLConnection connection = connectionRef.get();
                if (connection != null) {
                    connection.disconnect();
                }
                releaseActiveStream(released);
            });
            sink.onDispose(() -> {
                deadlineTask.dispose();
                releaseActiveStream(released);
            });
            try {
                long deadlineAtMs = System.currentTimeMillis() + maxStreamDurationMs;
                JsonNode resolvedRequest = llmTemplateRenderer.render(resolveTemplate(provider), buildVariables(provider, request));
                Map<String, String> headers = buildHeaders(provider);
                String body = objectMapper.writeValueAsString(resolvedRequest);
                String mappingJson = resolveResponseMapping(provider);
                JsonNode mapping = objectMapper.readTree(mappingJson);
                String doneFlag = mapping.path("streamDoneFlagPath").asText("[DONE]");
                String chunkPath = mapping.path("streamChunkPath").asText();
                String finishReasonPath = mapping.path("finishReasonPath").asText();

                HttpURLConnection connection = openConnection(provider, headers, endpoint);
                connectionRef.set(connection);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int statusCode = connection.getResponseCode();
                verifyEndpointStillPinned(endpoint);
                if (statusCode < 200 || statusCode >= 300) {
                    sink.error(new BusinessException(statusCode, "自定义模型流式调用失败"));
                    connection.disconnect();
                    return;
                }
                try {
                    consumeSse(new LimitedInputStream(connection.getInputStream(), maxResponseBytes), doneFlag, chunkPath, finishReasonPath, deadlineAtMs, sink);
                } finally {
                    connection.disconnect();
                }
                sink.complete();
            } catch (BusinessException e) {
                sink.error(e);
            } catch (Exception e) {
                sink.error(new BusinessException(503, "LLM 流式调用失败", "llm stream request failed", e));
            } finally {
                deadlineTask.dispose();
                releaseActiveStream(released);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void releaseActiveStream(AtomicBoolean released) {
        if (released.compareAndSet(false, true)) {
            activeStreams.release();
        }
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
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey(provider));
        }
        return headers;
    }

    private String resolveApiKey(RuntimeLlmProvider provider) {
        if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            return provider.getApiKey();
        }
        if (provider.getApiKeyCiphertext() != null && !provider.getApiKeyCiphertext().isBlank()) {
            return llmSecretCrypto.decrypt(provider.getApiKeyCiphertext());
        }
        throw new BusinessException(400, "Provider API Key 未配置");
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
        headers.forEach((key, value) -> masked.put(key, maskHeaderValue(key, value)));
        return objectMapper.writeValueAsString(masked);
    }

    private String maskHeaderValue(String key, String value) {
        if (!isSensitiveHeader(key)) {
            return value;
        }
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key)
                || "proxy-authorization".equalsIgnoreCase(key)) {
            return maskAuthorizationHeader(value);
        }
        return "****";
    }

    private boolean isSensitiveHeader(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return SENSITIVE_HEADER_NAMES.contains(normalized)
                || normalized.contains("api-key")
                || normalized.contains("apikey")
                || normalized.contains("secret")
                || normalized.contains("token");
    }

    private String maskAuthorizationHeader(String value) {
        if (value == null || value.isBlank()) {
            return "****";
        }
        String trimmed = value.trim();
        int separator = trimmed.indexOf(' ');
        if (separator <= 0) {
            return "****";
        }
        return trimmed.substring(0, separator) + " ****";
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

    private ValidatedEndpoint validateEndpointUrl(String endpointUrl) {
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
        if (uri.getUserInfo() != null) {
            throw new BusinessException(400, "接口地址不能包含用户信息");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(400, "接口地址缺少主机名");
        }
        rejectAmbiguousIpLiteral(host);
        Set<String> pinnedAddresses = resolveEndpointAddresses(host);
        if (!allowPrivateEndpoints) {
            if (containsPrivateOrLocalAddress(pinnedAddresses)) {
                throw new BusinessException(400, "接口地址不能指向本机或内网地址");
            }
            validatePublicEndpointPort(uri);
        }
        return new ValidatedEndpoint(uri, host.trim().toLowerCase(Locale.ROOT), pinnedAddresses);
    }

    private HttpURLConnection openConnection(RuntimeLlmProvider provider,
                                             Map<String, String> headers,
                                             ValidatedEndpoint endpoint) throws IOException {
        verifyEndpointStillPinned(endpoint);
        URL url = endpoint.uri().toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(provider.getConnectTimeoutMs());
        connection.setReadTimeout(provider.getReadTimeoutMs());
        connection.setDoOutput(true);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.forEach(connection::setRequestProperty);
        return connection;
    }

    private void verifyEndpointStillPinned(ValidatedEndpoint endpoint) {
        Set<String> currentAddresses = resolveEndpointAddresses(endpoint.host());
        if (!allowPrivateEndpoints && containsPrivateOrLocalAddress(currentAddresses)) {
            throw new BusinessException(400, "接口地址解析结果不能指向本机或内网地址");
        }
        Set<String> overlap = new HashSet<>(currentAddresses);
        overlap.retainAll(endpoint.pinnedAddresses());
        if (overlap.isEmpty()) {
            throw new BusinessException(400, "接口地址 DNS 解析结果发生变化，请重试");
        }
    }

    private String readResponseBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream inputStream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (inputStream == null) {
            return null;
        }
        try (InputStream limited = new LimitedInputStream(inputStream, maxResponseBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = limited.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void validatePublicEndpointPort(URI uri) {
        int port = uri.getPort();
        if (port == -1) {
            return;
        }
        if (!ALLOWED_PUBLIC_PORTS.contains(port)) {
            throw new BusinessException(400, "接口地址端口不在允许范围内");
        }
    }

    private Set<String> resolveEndpointAddresses(String host) {
        String normalized = host.trim().toLowerCase();
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return Set.of("127.0.0.1");
        }
        try {
            return Arrays.stream(InetAddress.getAllByName(normalized))
                    .map(InetAddress::getHostAddress)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (UnknownHostException e) {
            throw new BusinessException(400, "接口地址主机名无法解析", "llm endpoint host cannot be resolved", e);
        }
    }

    private boolean containsPrivateOrLocalAddress(Set<String> addresses) {
        for (String address : addresses) {
            try {
                if (isPrivateOrLocalAddress(InetAddress.getByName(address))) {
                    return true;
                }
            } catch (UnknownHostException e) {
                throw new BusinessException(400, "接口地址主机名无法解析", "llm endpoint host cannot be resolved", e);
            }
        }
        return false;
    }

    private boolean isPrivateOrLocalAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isCloudMetadataAddress(address)
                || isUniqueLocalIpv6(address);
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isCloudMetadataAddress(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        int fourth = bytes[3] & 0xff;
        return first == 100 && second == 100 && third == 100 && fourth == 200;
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        int firstByte = address.getAddress()[0] & 0xff;
        return (firstByte & 0xfe) == 0xfc;
    }

    private void rejectAmbiguousIpLiteral(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            return;
        }
        if (normalized.startsWith("0x") || normalized.matches("\\d+")) {
            throw new BusinessException(400, "接口地址 IP 表达不明确");
        }
        if (normalized.matches("[0-9a-fx.]+") && !isCanonicalIpv4(normalized)) {
            throw new BusinessException(400, "接口地址 IP 表达不明确");
        }
    }

    private boolean isCanonicalIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isBlank() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            if (part.length() > 1 && part.startsWith("0")) {
                return false;
            }
            int octet = Integer.parseInt(part);
            if (octet < 0 || octet > 255) {
                return false;
            }
        }
        return true;
    }

    private record ValidatedEndpoint(URI uri, String host, Set<String> pinnedAddresses) {
    }

    private void consumeSse(InputStream inputStream,
                            String doneFlag,
                            String chunkPath,
                            String finishReasonPath,
                            long deadlineAtMs,
                            reactor.core.publisher.FluxSink<Map<String, Object>> sink) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder block = new StringBuilder();
            String line;
            int eventCount = 0;
            while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                enforceStreamDeadline(deadlineAtMs);
                if (line.isBlank()) {
                    if (emitSseBlock(block.toString(), doneFlag, chunkPath, finishReasonPath, sink)) {
                        eventCount = incrementSseEventCount(eventCount);
                    }
                    block.setLength(0);
                    continue;
                }
                block.append(line).append('\n');
            }
            if (!block.isEmpty() && !sink.isCancelled()) {
                enforceStreamDeadline(deadlineAtMs);
                if (emitSseBlock(block.toString(), doneFlag, chunkPath, finishReasonPath, sink)) {
                    incrementSseEventCount(eventCount);
                }
            }
        }
    }

    private void enforceStreamDeadline(long deadlineAtMs) {
        if (System.currentTimeMillis() > deadlineAtMs) {
            throw new BusinessException(408, "LLM 流式响应超时");
        }
    }

    private int incrementSseEventCount(int eventCount) {
        int nextCount = eventCount + 1;
        if (nextCount > maxSseEvents) {
            throw new BusinessException(413, "LLM 流式响应事件数量过多");
        }
        return nextCount;
    }

    private boolean emitSseBlock(String block,
                                 String doneFlag,
                                 String chunkPath,
                                 String finishReasonPath,
                                 reactor.core.publisher.FluxSink<Map<String, Object>> sink) throws IOException {
        if (block == null || block.isBlank()) {
            return false;
        }
        String payload = block.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (payload.isBlank()) {
            return false;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("payload", payload);
        if (doneFlag.equals(payload)) {
            event.put("done", true);
            sink.next(event);
            return true;
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
        return true;
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

    private static class LimitedInputStream extends FilterInputStream {

        private final int maxBytes;
        private int bytesRead;

        LimitedInputStream(InputStream inputStream, int maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result != -1) {
                countBytes(1);
            }
            return result;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                countBytes(count);
            }
            return count;
        }

        private void countBytes(int count) {
            bytesRead += count;
            if (bytesRead > maxBytes) {
                throw new BusinessException(413, "LLM 调试响应过大");
            }
        }
    }
}
