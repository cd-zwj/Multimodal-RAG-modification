package com.example.demo.service.llm;

import com.example.demo.mapper.LlmProviderConfigMapper;
import com.example.demo.mapper.LlmModelConfigMapper;
import com.example.demo.model.llm.LlmModelConfig;
import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmProtocolType;
import com.example.demo.model.llm.LlmProviderConfig;
import com.example.demo.model.llm.LlmProviderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmProviderRegistryTest {

    @Test
    void shouldLoadEnabledProvidersIntoRegistry() {
        LlmProviderConfigMapper mapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        LlmProviderConfig provider = new LlmProviderConfig();
        provider.setId("p1");
        provider.setProviderCode("custom-openai");
        provider.setProviderName("自定义 OpenAI");
        provider.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        provider.setAuthType(LlmAuthType.BEARER);
        provider.setApiKeyCiphertext("cipher");
        provider.setEndpointUrl("https://example.com/v1/chat/completions");
        provider.setDefaultModel("qwen-plus");
        provider.setStatus(LlmProviderStatus.ENABLED);

        when(mapper.selectEnabledProviders()).thenReturn(List.of(provider));
        when(modelMapper.selectEnabledModels()).thenReturn(java.util.List.of());

        LlmProviderRegistry registry = new LlmProviderRegistry(mapper, modelMapper, crypto);
        registry.reload();

        RuntimeLlmProvider runtime = registry.getRequired("custom-openai");
        assertNull(runtime.getApiKey());
        assertEquals("cipher", runtime.getApiKeyCiphertext());
        assertEquals("https://example.com/v1/chat/completions", runtime.getEndpointUrl());
        verify(crypto, never()).decrypt(any());
    }

    @Test
    void shouldThrowWhenProviderCodeIsUnknown() {
        LlmProviderConfigMapper mapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        when(mapper.selectEnabledProviders()).thenReturn(List.of());
        when(modelMapper.selectEnabledModels()).thenReturn(java.util.List.of());

        LlmProviderRegistry registry = new LlmProviderRegistry(mapper, modelMapper, crypto);
        registry.reload();

        assertThrows(IllegalArgumentException.class, () -> registry.getRequired("missing"));
    }

    @Test
    void shouldResolveModelRuntimeWithModelDefaultsAndCapabilities() {
        LlmProviderConfigMapper mapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        LlmProviderConfig provider = new LlmProviderConfig();
        provider.setId("p1");
        provider.setProviderCode("custom-openai");
        provider.setProviderName("自定义 OpenAI");
        provider.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        provider.setAuthType(LlmAuthType.BEARER);
        provider.setApiKeyCiphertext("cipher");
        provider.setEndpointUrl("https://example.com/v1/chat/completions");
        provider.setDefaultModel("provider-default");
        provider.setStatus(LlmProviderStatus.ENABLED);
        LlmModelConfig model = new LlmModelConfig();
        model.setId("m1");
        model.setProviderId("p1");
        model.setModelCode("qwen-plus-prod");
        model.setRemoteModelName("qwen-plus");
        model.setDefaultParamsJson("{\"temperature\":0.4}");
        model.setCapabilitiesJson("{\"chat\":true,\"stream\":false}");
        model.setStatus(LlmProviderStatus.ENABLED);
        when(mapper.selectEnabledProviders()).thenReturn(List.of(provider));
        when(mapper.selectById("p1")).thenReturn(provider);
        when(modelMapper.selectEnabledModels()).thenReturn(List.of(model));

        LlmProviderRegistry registry = new LlmProviderRegistry(mapper, modelMapper, crypto);
        registry.reload();

        RuntimeLlmProvider runtime = registry.getRequiredByModelCode("qwen-plus-prod");

        assertEquals("qwen-plus", runtime.getDefaultModel());
        assertEquals("{\"temperature\":0.4}", runtime.getDefaultParamsJson());
        assertEquals("{\"chat\":true,\"stream\":false}", runtime.getCapabilitiesJson());
        assertNull(runtime.getApiKey());
        assertEquals("cipher", runtime.getApiKeyCiphertext());
        verify(crypto, never()).decrypt(any());
    }
}

