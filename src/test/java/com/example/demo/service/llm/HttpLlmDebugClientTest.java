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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

    private HttpLlmDebugClient client() {
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        when(crypto.mask("sk-test")).thenReturn("****");
        return new HttpLlmDebugClient(
                MAPPER,
                new LlmTemplateRenderer(MAPPER),
                new LlmResponseExtractor(MAPPER),
                crypto
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
}
