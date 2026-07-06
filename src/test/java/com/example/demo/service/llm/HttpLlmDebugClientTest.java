package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import com.example.demo.model.dto.llm.LlmDebugRequest;
import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmProtocolType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpLlmDebugClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldMergeModelDefaultParamsIntoResolvedRequest() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody, """
                {"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}
                """);
        try {
            HttpLlmDebugClient client = client();
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat")
                    .toBuilder()
                    .defaultParamsJson("{\"temperature\":0.3,\"topP\":0.8,\"maxTokens\":2048}")
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");
            request.setParams(Map.of("temperature", 0.7));

            client.debug(provider, request);

            JsonNode body = MAPPER.readTree(capturedBody.get());
            assertEquals(0.7d, body.get("temperature").asDouble(), 0.0001d);
            assertEquals(0.8d, body.get("topP").asDouble(), 0.0001d);
            assertEquals(2048, body.get("maxTokens").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectStreamWhenModelCapabilityDisablesStream() {
        HttpLlmDebugClient client = client();
        RuntimeLlmProvider provider = provider("https://example.com/v1/chat/completions")
                .toBuilder()
                .capabilitiesJson("{\"chat\":true,\"stream\":false}")
                .build();
        LlmDebugRequest request = new LlmDebugRequest();
        request.setMessage("你好");
        request.setStream(true);

        BusinessException error = assertThrows(BusinessException.class, () -> client.debug(provider, request));

        assertEquals(400, error.getCode());
    }

    @Test
    void shouldRejectPrivateOrLocalEndpointUrls() {
        HttpLlmDebugClient client = client(false);
        LlmDebugRequest request = new LlmDebugRequest();
        request.setMessage("你好");

        for (String url : List.of(
                "http://localhost:8080/chat",
                "http://127.0.0.1:8080/chat",
                "http://10.0.0.1/chat",
                "http://169.254.169.254/latest/meta-data",
                "ftp://example.com/chat",
                "https://user@example.com/chat",
                "https://example.com:22/chat",
                "http://0177.0.0.1/chat",
                "http://0x7f000001/chat",
                "http://2130706433/chat",
                "http://[::ffff:127.0.0.1]/chat",
                "http://[fc00::1]/chat",
                "http://100.100.100.200/latest/meta-data"
        )) {
            RuntimeLlmProvider provider = provider(url);
            BusinessException error = assertThrows(BusinessException.class, () -> client.debug(provider, request));
            assertEquals(400, error.getCode());
        }
    }

    @Test
    void shouldNotFollowRedirectsForDebugRequests() throws Exception {
        AtomicReference<String> redirectedBody = new AtomicReference<>();
        HttpServer server = startRedirectServer(redirectedBody);
        try {
            HttpLlmDebugClient client = client();
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/redirect");
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");

            var response = client.debug(provider, request);

            assertEquals(302, response.getHttpStatus());
            assertTrue(redirectedBody.get() == null || redirectedBody.get().isBlank());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotFollowRedirectsForStreamingRequests() throws Exception {
        AtomicReference<String> redirectedBody = new AtomicReference<>();
        HttpServer server = startRedirectServer(redirectedBody);
        try {
            HttpLlmDebugClient client = client();
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/redirect")
                    .toBuilder()
                    .responseMappingJson("{\"mode\":\"SSE\",\"streamChunkPath\":\"choices.0.delta.content\",\"finishReasonPath\":\"choices.0.finish_reason\"}")
                    .capabilitiesJson("{\"chat\":true,\"stream\":true}")
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");
            request.setStream(true);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> client.streamContent(provider, request).collectList().block()
            );

            assertEquals(302, error.getCode());
            assertTrue(redirectedBody.get() == null || redirectedBody.get().isBlank());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectOversizedDebugResponse() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody, """
                {"choices":[{"message":{"content":"response-is-too-large"},"finish_reason":"stop"}]}
                """);
        try {
            HttpLlmDebugClient client = client(true, 16, 10);
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat");
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");

            BusinessException error = assertThrows(BusinessException.class, () -> client.debug(provider, request));

            assertEquals(413, error.getCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectTooManySseEvents() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody, """
                data: {"choices":[{"delta":{"content":"一"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"二"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"三"},"finish_reason":"stop"}]}

                """);
        try {
            HttpLlmDebugClient client = client(true, 1024, 2);
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat")
                    .toBuilder()
                    .responseMappingJson("{\"mode\":\"SSE\",\"streamChunkPath\":\"choices.0.delta.content\",\"finishReasonPath\":\"choices.0.finish_reason\"}")
                    .capabilitiesJson("{\"chat\":true,\"stream\":true}")
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");
            request.setStream(true);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> client.streamContent(provider, request).collectList().block()
            );

            assertEquals(413, error.getCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectStreamingWhenActiveStreamLimitIsExhausted() {
        HttpLlmDebugClient client = client(true, 1024, 1000, 120000, 0);
        RuntimeLlmProvider provider = provider("http://localhost:65530/chat")
                .toBuilder()
                .responseMappingJson("{\"mode\":\"SSE\",\"streamChunkPath\":\"choices.0.delta.content\",\"finishReasonPath\":\"choices.0.finish_reason\"}")
                .capabilitiesJson("{\"chat\":true,\"stream\":true}")
                .build();
        LlmDebugRequest request = new LlmDebugRequest();
        request.setMessage("你好");
        request.setStream(true);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> client.streamContent(provider, request).collectList().block()
        );

        assertEquals(429, error.getCode());
    }

    @Test
    void shouldAbortStreamingWhenTotalDurationDeadlineIsExceeded() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startDelayedServer(capturedBody, 100, """
                data: {"choices":[{"delta":{"content":"慢"},"finish_reason":null}]}

                """);
        try {
            HttpLlmDebugClient client = client(true, 1024, 1000, 1, 10);
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat")
                    .toBuilder()
                    .responseMappingJson("{\"mode\":\"SSE\",\"streamChunkPath\":\"choices.0.delta.content\",\"finishReasonPath\":\"choices.0.finish_reason\"}")
                    .capabilitiesJson("{\"chat\":true,\"stream\":true}")
                    .readTimeoutMs(5000)
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");
            request.setStream(true);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> client.streamContent(provider, request).collectList().block()
            );

            assertEquals(408, error.getCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMaskSensitiveDebugHeadersBeyondBearerAuthorization() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody, """
                {"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}
                """);
        try {
            HttpLlmDebugClient client = client();
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat")
                    .toBuilder()
                    .authType(LlmAuthType.HEADER)
                    .defaultHeadersJson("""
                            {
                              "X-API-Key": "custom-api-key",
                              "api-key": "lowercase-api-key",
                              "Authorization": "Basic dXNlcjpwYXNz",
                              "Cookie": "sid=session-secret",
                              "Proxy-Authorization": "Basic cHJveHk=",
                              "OpenAI-Organization": "org-secret",
                              "X-Trace-Id": "trace-123"
                            }
                            """)
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");

            var response = client.debug(provider, request);

            JsonNode maskedHeaders = MAPPER.readTree(response.getMaskedHeadersJson());
            assertEquals("****", maskedHeaders.get("X-API-Key").asText());
            assertEquals("****", maskedHeaders.get("api-key").asText());
            assertEquals("Basic ****", maskedHeaders.get("Authorization").asText());
            assertEquals("****", maskedHeaders.get("Cookie").asText());
            assertEquals("Basic ****", maskedHeaders.get("Proxy-Authorization").asText());
            assertEquals("****", maskedHeaders.get("OpenAI-Organization").asText());
            assertEquals("trace-123", maskedHeaders.get("X-Trace-Id").asText());

            String maskedJson = response.getMaskedHeadersJson();
            assertTrue(!maskedJson.contains("custom-api-key"));
            assertTrue(!maskedJson.contains("dXNlcjpwYXNz"));
            assertTrue(!maskedJson.contains("session-secret"));
            assertTrue(!maskedJson.contains("org-secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDecryptPersistedApiKeyOnlyWhenSendingDebugRequest() throws Exception {
        AtomicReference<String> capturedAuthorization = new AtomicReference<>();
        HttpServer server = startHeaderServer(capturedAuthorization);
        try {
            LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
            when(crypto.decrypt("cipher")).thenReturn("plain-api-key");
            HttpLlmDebugClient client = new HttpLlmDebugClient(
                    MAPPER,
                    new LlmTemplateRenderer(MAPPER),
                    new LlmResponseExtractor(MAPPER),
                    crypto,
                    true
            );
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat")
                    .toBuilder()
                    .apiKey(null)
                    .apiKeyCiphertext("cipher")
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");

            client.debug(provider, request);

            assertEquals("Bearer plain-api-key", capturedAuthorization.get());
            verify(crypto).decrypt("cipher");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseSseEventsForStreamingDebug() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody, """
                data: {"choices":[{"delta":{"content":"你"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"好"},"finish_reason":"stop"}]}

                data: [DONE]

                """);
        try {
            HttpLlmDebugClient client = client();
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat")
                    .toBuilder()
                    .responseMappingJson("{\"mode\":\"SSE\",\"streamChunkPath\":\"choices.0.delta.content\",\"finishReasonPath\":\"choices.0.finish_reason\"}")
                    .capabilitiesJson("{\"chat\":true,\"stream\":true}")
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");
            request.setStream(true);

            var response = client.debug(provider, request);

            assertTrue(response.isSuccess());
            assertEquals("你好", response.getParsedContent());
            assertEquals(3, response.getStreamEvents().size());
            assertTrue(MAPPER.readTree(capturedBody.get()).get("stream").asBoolean());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldStreamChunksFromRemoteSseResponse() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody, """
                data: {"choices":[{"delta":{"content":"你"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"好"},"finish_reason":"stop"}]}

                data: [DONE]

                """);
        try {
            HttpLlmDebugClient client = client();
            RuntimeLlmProvider provider = provider("http://localhost:" + server.getAddress().getPort() + "/chat")
                    .toBuilder()
                    .responseMappingJson("{\"mode\":\"SSE\",\"streamChunkPath\":\"choices.0.delta.content\",\"finishReasonPath\":\"choices.0.finish_reason\"}")
                    .capabilitiesJson("{\"chat\":true,\"stream\":true}")
                    .build();
            LlmDebugRequest request = new LlmDebugRequest();
            request.setMessage("你好");
            request.setStream(true);

            List<String> chunks = client.streamContent(provider, request).collectList().block();

            assertEquals(List.of("你", "好"), chunks);
            assertTrue(MAPPER.readTree(capturedBody.get()).get("stream").asBoolean());
        } finally {
            server.stop(0);
        }
    }

    private HttpLlmDebugClient client() {
        return client(true);
    }

    private HttpLlmDebugClient client(boolean allowPrivateEndpoints) {
        return client(allowPrivateEndpoints, 1024 * 1024, 1000);
    }

    private HttpLlmDebugClient client(boolean allowPrivateEndpoints, int maxResponseBytes, int maxSseEvents) {
        return client(allowPrivateEndpoints, maxResponseBytes, maxSseEvents, 120000, 50);
    }

    private HttpLlmDebugClient client(boolean allowPrivateEndpoints,
                                      int maxResponseBytes,
                                      int maxSseEvents,
                                      long maxStreamDurationMs,
                                      int maxActiveStreams) {
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        when(crypto.mask("sk-test")).thenReturn("****");
        return new HttpLlmDebugClient(
                MAPPER,
                new LlmTemplateRenderer(MAPPER),
                new LlmResponseExtractor(MAPPER),
                crypto,
                allowPrivateEndpoints,
                maxResponseBytes,
                maxSseEvents,
                maxStreamDurationMs,
                maxActiveStreams
        );
    }

    private RuntimeLlmProvider provider(String endpointUrl) {
        return RuntimeLlmProvider.builder()
                .id("p1")
                .providerCode("custom-openai")
                .providerName("自定义 OpenAI")
                .protocolType(LlmProtocolType.OPENAI_COMPATIBLE)
                .authType(LlmAuthType.BEARER)
                .endpointUrl(endpointUrl)
                .apiKey("sk-test")
                .defaultModel("qwen-plus")
                .requestTemplateJson("""
                        {
                          "model": "{{model}}",
                          "messages": [{"role":"user","content":"{{message}}"}],
                          "temperature": "{{params.temperature}}",
                          "topP": "{{params.topP}}",
                          "maxTokens": "{{params.maxTokens}}",
                          "stream": "{{stream}}"
                        }
                        """)
                .responseMappingJson("{\"mode\":\"JSON\",\"contentPath\":\"choices.0.message.content\",\"finishReasonPath\":\"choices.0.finish_reason\"}")
                .connectTimeoutMs(5000)
                .readTimeoutMs(30000)
                .build();
    }

    private HttpServer startServer(AtomicReference<String> capturedBody, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startDelayedServer(AtomicReference<String> capturedBody,
                                          long delayMs,
                                          String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startRedirectServer(AtomicReference<String> redirectedBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirectedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = """
                    {"choices":[{"message":{"content":"redirected"},"finish_reason":"stop"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startHeaderServer(AtomicReference<String> capturedAuthorization) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = """
                    {"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
