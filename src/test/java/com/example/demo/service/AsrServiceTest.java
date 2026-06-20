package com.example.demo.service;

import com.example.demo.exception.AsrUnavailableException;
import com.example.demo.service.asr.AsrFallbackProperties;
import com.example.demo.service.asr.AsrProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsrServiceTest {

    @Test
    void shouldUseFirstAvailableProviderInConfiguredOrder() {
        StubAsrProvider primary = StubAsrProvider.available("aliyun-nls", "primary text");
        StubAsrProvider fallback = StubAsrProvider.available("dashscope-paraformer", "fallback text");
        AsrService service = new AsrService(List.of(fallback, primary), properties("aliyun-nls", "dashscope-paraformer"));

        String text = service.transcribe(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertEquals("primary text", text);
        assertEquals(1, primary.calls);
        assertEquals(0, fallback.calls);
    }

    @Test
    void shouldFallbackWhenPrimaryProviderThrows() {
        StubAsrProvider primary = StubAsrProvider.failing("aliyun-nls");
        StubAsrProvider fallback = StubAsrProvider.available("dashscope-paraformer", "fallback text");
        AsrService service = new AsrService(List.of(primary, fallback), properties("aliyun-nls", "dashscope-paraformer"));

        String text = service.transcribe(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertEquals("fallback text", text);
        assertEquals(1, primary.calls);
        assertEquals(1, fallback.calls);
    }

    @Test
    void shouldThrowWhenNoProviderIsAvailable() {
        StubAsrProvider primary = StubAsrProvider.unavailable("aliyun-nls");
        StubAsrProvider fallback = StubAsrProvider.unavailable("dashscope-paraformer");
        AsrService service = new AsrService(List.of(primary, fallback), properties("aliyun-nls", "dashscope-paraformer"));

        assertThrows(
                AsrUnavailableException.class,
                () -> service.transcribe(new ByteArrayInputStream(new byte[]{1, 2, 3}))
        );
    }

    private static AsrFallbackProperties properties(String... providers) {
        AsrFallbackProperties properties = new AsrFallbackProperties();
        properties.setProviderOrder(List.of(providers));
        return properties;
    }

    private static final class StubAsrProvider implements AsrProvider {
        private final String name;
        private final boolean available;
        private final String result;
        private final boolean failing;
        private int calls;

        private StubAsrProvider(String name, boolean available, String result, boolean failing) {
            this.name = name;
            this.available = available;
            this.result = result;
            this.failing = failing;
        }

        static StubAsrProvider available(String name, String result) {
            return new StubAsrProvider(name, true, result, false);
        }

        static StubAsrProvider unavailable(String name) {
            return new StubAsrProvider(name, false, null, false);
        }

        static StubAsrProvider failing(String name) {
            return new StubAsrProvider(name, true, null, true);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String transcribe(byte[] audioData) {
            calls++;
            if (failing) {
                throw new AsrUnavailableException("failed");
            }
            return result;
        }
    }
}
