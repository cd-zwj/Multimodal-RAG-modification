package com.example.demo.service.llm;

import com.example.demo.mapper.LlmDebugSessionMapper;
import com.example.demo.mapper.LlmModelConfigMapper;
import com.example.demo.mapper.LlmProviderConfigMapper;
import com.example.demo.exception.BusinessException;
import com.example.demo.model.dto.ApiResponse;
import com.example.demo.model.dto.llm.CreateLlmProviderRequest;
import com.example.demo.model.dto.llm.CreateLlmModelRequest;
import com.example.demo.model.dto.llm.LlmDebugRequest;
import com.example.demo.model.dto.llm.LlmDebugResponse;
import com.example.demo.model.dto.llm.LlmProviderResponse;
import com.example.demo.model.dto.llm.UpdateLlmModelRequest;
import com.example.demo.model.dto.llm.UpdateLlmProviderRequest;
import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmDebugSession;
import com.example.demo.model.llm.LlmModelConfig;
import com.example.demo.model.llm.LlmProtocolType;
import com.example.demo.model.llm.LlmProviderConfig;
import com.example.demo.model.llm.LlmProviderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmProviderApplicationServiceTest {

    @Test
    void shouldCreateProviderPersistAndRegister() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmDebugSessionMapper debugSessionMapper = mock(LlmDebugSessionMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        HttpLlmDebugClient debugClient = mock(HttpLlmDebugClient.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);

        when(providerMapper.selectByProviderCode("custom-openai")).thenReturn(null);
        when(authContextService.getCurrentUserId()).thenReturn("u1");
        when(crypto.encrypt("sk-test")).thenReturn("cipher");
        when(crypto.mask("sk-test")).thenReturn("****");

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                debugSessionMapper,
                registry,
                crypto,
                debugClient,
                authContextService,
                new ObjectMapper()
        );

        CreateLlmProviderRequest request = new CreateLlmProviderRequest();
        request.setProviderCode("custom-openai");
        request.setProviderName("自定义 OpenAI");
        request.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        request.setAuthType(LlmAuthType.BEARER);
        request.setEndpointUrl("https://example.com/v1/chat/completions");
        request.setApiKey("sk-test");
        request.setDefaultModel("qwen-plus");

        ApiResponse<LlmProviderResponse> response = service.createProvider(request);

        assertEquals(200, response.getCode());
        assertEquals("custom-openai", response.getData().getProviderCode());
        assertEquals(LlmProviderStatus.ENABLED, response.getData().getStatus());
        verify(providerMapper).insert(org.mockito.ArgumentMatchers.<LlmProviderConfig>any());
        verify(registry).register(any(LlmProviderConfig.class));
        verify(debugSessionMapper, never()).insert(org.mockito.ArgumentMatchers.<com.example.demo.model.llm.LlmDebugSession>any());
    }

    @Test
    void shouldPersistDebugSessionAfterDebugging() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmDebugSessionMapper debugSessionMapper = mock(LlmDebugSessionMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        HttpLlmDebugClient debugClient = mock(HttpLlmDebugClient.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);
        AtomicReference<LlmDebugSession> capturedSession = new AtomicReference<>();

        RuntimeLlmProvider provider = RuntimeLlmProvider.builder()
                .id("p1")
                .providerCode("custom-openai")
                .endpointUrl("https://example.com/v1/chat/completions")
                .defaultModel("qwen-plus")
                .protocolType(LlmProtocolType.OPENAI_COMPATIBLE)
                .authType(LlmAuthType.BEARER)
                .apiKey("sk-test")
                .connectTimeoutMs(5000)
                .readTimeoutMs(30000)
                .build();
        LlmDebugResponse debugResponse = new LlmDebugResponse();
        debugResponse.setSuccess(true);
        debugResponse.setHttpStatus(200);
        debugResponse.setLatencyMs(123L);
        debugResponse.setResolvedRequestJson("{\"model\":\"qwen-plus\"}");
        debugResponse.setMaskedHeadersJson("{\"Authorization\":\"Bearer ****\"}");
        debugResponse.setRawResponseText("{\"choices\":[]}");
        debugResponse.setParsedContent("测试成功");

        when(registry.getRequired("custom-openai")).thenReturn(provider);
        when(debugClient.debug(any(), any())).thenReturn(debugResponse);
        when(authContextService.getCurrentUserId()).thenReturn("u1");
        when(debugSessionMapper.insert(org.mockito.ArgumentMatchers.<LlmDebugSession>any())).thenAnswer(invocation -> {
            capturedSession.set(invocation.getArgument(0));
            return 1;
        });

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                debugSessionMapper,
                registry,
                crypto,
                debugClient,
                authContextService,
                new ObjectMapper()
        );

        LlmDebugRequest request = new LlmDebugRequest();
        request.setProviderCode("custom-openai");
        request.setMessage("你好");
        request.setSystemPrompt("你是一个助手");
        request.setStream(false);
        request.setParams(Map.of("temperature", 0.7));

        ApiResponse<LlmDebugResponse> result = service.debug(request);

        assertEquals(200, result.getCode());
        assertTrue(result.getData().isSuccess());
        verify(debugSessionMapper).insert(org.mockito.ArgumentMatchers.<LlmDebugSession>any());
        assertEquals("custom-openai", capturedSession.get().getProviderCode());
        assertEquals(Boolean.TRUE, capturedSession.get().getSuccess());
    }

    @Test
    void shouldListEnabledProviders() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmDebugSessionMapper debugSessionMapper = mock(LlmDebugSessionMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        HttpLlmDebugClient debugClient = mock(HttpLlmDebugClient.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);

        LlmProviderConfig config = new LlmProviderConfig();
        config.setId("p1");
        config.setProviderCode("custom-openai");
        config.setProviderName("自定义 OpenAI");
        config.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        config.setAuthType(LlmAuthType.BEARER);
        config.setEndpointUrl("https://example.com/v1/chat/completions");
        config.setDefaultModel("qwen-plus");
        config.setConnectTimeoutMs(5000);
        config.setReadTimeoutMs(30000);
        config.setVersion(1);
        config.setStatus(LlmProviderStatus.ENABLED);
        config.setRemark("test");

        RuntimeLlmProvider runtimeProvider = RuntimeLlmProvider.builder()
                .id("p1")
                .providerCode("custom-openai")
                .providerName("自定义 OpenAI")
                .protocolType(LlmProtocolType.OPENAI_COMPATIBLE)
                .authType(LlmAuthType.BEARER)
                .endpointUrl("https://example.com/v1/chat/completions")
                .apiKey("sk-test")
                .defaultModel("qwen-plus")
                .connectTimeoutMs(5000)
                .readTimeoutMs(30000)
                .build();

        when(providerMapper.selectEnabledProviders()).thenReturn(java.util.List.of(config));
        when(registry.getRequired("custom-openai")).thenReturn(runtimeProvider);
        when(crypto.mask("sk-test")).thenReturn("****");
        when(crypto.decrypt(any())).thenReturn("sk-test");

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                debugSessionMapper,
                registry,
                crypto,
                debugClient,
                authContextService,
                new ObjectMapper()
        );

        ApiResponse<java.util.List<LlmProviderResponse>> result = service.listProviders();

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("custom-openai", result.getData().get(0).getProviderCode());
    }
    @Test
    void shouldDebugTemporaryProviderWithoutPersistingOrRegisterLookup() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmDebugSessionMapper debugSessionMapper = mock(LlmDebugSessionMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        HttpLlmDebugClient debugClient = mock(HttpLlmDebugClient.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);
        ArgumentCaptor<RuntimeLlmProvider> providerCaptor = ArgumentCaptor.forClass(RuntimeLlmProvider.class);

        LlmDebugResponse debugResponse = new LlmDebugResponse();
        debugResponse.setSuccess(true);
        debugResponse.setHttpStatus(200);
        debugResponse.setParsedContent("临时调试成功");
        when(debugClient.debug(providerCaptor.capture(), any())).thenReturn(debugResponse);

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                debugSessionMapper,
                registry,
                crypto,
                debugClient,
                authContextService,
                new ObjectMapper()
        );

        CreateLlmProviderRequest providerConfig = new CreateLlmProviderRequest();
        providerConfig.setProviderCode("draft-openai");
        providerConfig.setProviderName("草稿 OpenAI");
        providerConfig.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        providerConfig.setAuthType(LlmAuthType.BEARER);
        providerConfig.setEndpointUrl("https://example.com/v1/chat/completions");
        providerConfig.setApiKey("sk-draft");
        providerConfig.setDefaultModel("gpt-test");
        providerConfig.setConnectTimeoutMs(5000);
        providerConfig.setReadTimeoutMs(30000);

        LlmDebugRequest request = new LlmDebugRequest();
        request.setProviderConfig(providerConfig);
        request.setMessage("你好");
        request.setStream(false);

        ApiResponse<LlmDebugResponse> result = service.debug(request);

        assertEquals(200, result.getCode());
        assertEquals("临时调试成功", result.getData().getParsedContent());
        assertEquals("draft-openai", providerCaptor.getValue().getProviderCode());
        assertEquals("sk-draft", providerCaptor.getValue().getApiKey());
        verify(registry, never()).getRequired(any());
        verify(debugSessionMapper, never()).insert(org.mockito.ArgumentMatchers.<LlmDebugSession>any());
        verify(providerMapper, never()).insert(org.mockito.ArgumentMatchers.<LlmProviderConfig>any());
    }
    @Test
    void shouldUpdateProviderWithoutChangingApiKeyWhenApiKeyIsBlank() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmDebugSessionMapper debugSessionMapper = mock(LlmDebugSessionMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        HttpLlmDebugClient debugClient = mock(HttpLlmDebugClient.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);
        ArgumentCaptor<LlmProviderConfig> configCaptor = ArgumentCaptor.forClass(LlmProviderConfig.class);

        LlmProviderConfig existing = new LlmProviderConfig();
        existing.setId("p1");
        existing.setProviderCode("custom-openai");
        existing.setProviderName("旧名称");
        existing.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        existing.setAuthType(LlmAuthType.BEARER);
        existing.setEndpointUrl("https://old.example.com/v1/chat/completions");
        existing.setApiKeyCiphertext("cipher-old");
        existing.setDefaultModel("old-model");
        existing.setConnectTimeoutMs(5000);
        existing.setReadTimeoutMs(30000);
        existing.setVersion(2);
        existing.setStatus(LlmProviderStatus.ENABLED);

        when(providerMapper.selectById("p1")).thenReturn(existing);
        when(authContextService.getCurrentUserId()).thenReturn("u1");
        when(crypto.decrypt("cipher-old")).thenReturn("sk-old");
        when(crypto.mask("sk-old")).thenReturn("****old");

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                debugSessionMapper,
                registry,
                crypto,
                debugClient,
                authContextService,
                new ObjectMapper()
        );

        UpdateLlmProviderRequest request = new UpdateLlmProviderRequest();
        request.setProviderName("新名称");
        request.setProtocolType(LlmProtocolType.GENERIC_HTTP);
        request.setAuthType(LlmAuthType.BEARER);
        request.setEndpointUrl("https://new.example.com/v1/chat/completions");
        request.setApiKey(" ");
        request.setDefaultModel("new-model");
        request.setRequestTemplateJson("{}");
        request.setResponseMappingJson("{}");
        request.setConnectTimeoutMs(6000);
        request.setReadTimeoutMs(40000);
        request.setRemark("updated");

        ApiResponse<LlmProviderResponse> response = service.updateProvider("p1", request);

        assertEquals(200, response.getCode());
        assertEquals("custom-openai", response.getData().getProviderCode());
        assertEquals("新名称", response.getData().getProviderName());
        verify(crypto, never()).encrypt(any());
        verify(providerMapper).updateById(configCaptor.capture());
        verify(registry).register(configCaptor.getValue());
        assertEquals("cipher-old", configCaptor.getValue().getApiKeyCiphertext());
        assertEquals(3, configCaptor.getValue().getVersion());
    }

    @Test
    void shouldUpdateProviderAndReEncryptWhenApiKeyIsProvided() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmDebugSessionMapper debugSessionMapper = mock(LlmDebugSessionMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        HttpLlmDebugClient debugClient = mock(HttpLlmDebugClient.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);
        ArgumentCaptor<LlmProviderConfig> configCaptor = ArgumentCaptor.forClass(LlmProviderConfig.class);

        LlmProviderConfig existing = new LlmProviderConfig();
        existing.setId("p1");
        existing.setProviderCode("custom-openai");
        existing.setProviderName("旧名称");
        existing.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        existing.setAuthType(LlmAuthType.BEARER);
        existing.setEndpointUrl("https://old.example.com/v1/chat/completions");
        existing.setApiKeyCiphertext("cipher-old");
        existing.setDefaultModel("old-model");
        existing.setConnectTimeoutMs(5000);
        existing.setReadTimeoutMs(30000);
        existing.setVersion(1);
        existing.setStatus(LlmProviderStatus.ENABLED);

        when(providerMapper.selectById("p1")).thenReturn(existing);
        when(authContextService.getCurrentUserId()).thenReturn("u1");
        when(crypto.encrypt("sk-new")).thenReturn("cipher-new");
        when(crypto.mask("sk-new")).thenReturn("****new");

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                debugSessionMapper,
                registry,
                crypto,
                debugClient,
                authContextService,
                new ObjectMapper()
        );

        UpdateLlmProviderRequest request = new UpdateLlmProviderRequest();
        request.setProviderName("新名称");
        request.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        request.setAuthType(LlmAuthType.BEARER);
        request.setEndpointUrl("https://new.example.com/v1/chat/completions");
        request.setApiKey("sk-new");
        request.setDefaultModel("new-model");
        request.setConnectTimeoutMs(6000);
        request.setReadTimeoutMs(40000);

        service.updateProvider("p1", request);

        verify(providerMapper).updateById(configCaptor.capture());
        assertEquals("cipher-new", configCaptor.getValue().getApiKeyCiphertext());
        verify(registry).register(configCaptor.getValue());
    }

    @Test
    void shouldDeleteProviderAndUnregisterRuntimeCacheWhenNoModels() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmDebugSessionMapper debugSessionMapper = mock(LlmDebugSessionMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        HttpLlmDebugClient debugClient = mock(HttpLlmDebugClient.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);

        LlmProviderConfig existing = new LlmProviderConfig();
        existing.setId("p1");
        existing.setProviderCode("custom-openai");
        when(providerMapper.selectById("p1")).thenReturn(existing);

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                debugSessionMapper,
                registry,
                crypto,
                debugClient,
                authContextService,
                new ObjectMapper()
        );

        ApiResponse<Void> response = service.deleteProvider("p1");

        assertEquals(200, response.getCode());
        verify(providerMapper).deleteById("p1");
        verify(registry).unregister("custom-openai");
    }

    @Test
    void shouldRejectDeletingProviderWhenModelsExist() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmProviderConfig existing = new LlmProviderConfig();
        existing.setId("p1");
        existing.setProviderCode("custom-openai");
        when(providerMapper.selectById("p1")).thenReturn(existing);
        LlmModelConfig model = new LlmModelConfig();
        model.setProviderId("p1");
        model.setModelCode("qwen-plus-prod");
        when(modelMapper.selectAllModels()).thenReturn(java.util.List.of(model));

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                mock(LlmDebugSessionMapper.class),
                registry,
                mock(LlmSecretCrypto.class),
                mock(HttpLlmDebugClient.class),
                mock(com.example.demo.service.AuthContextService.class),
                new ObjectMapper()
        );

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteProvider("p1"));

        assertEquals(409, error.getCode());
        verify(providerMapper, never()).deleteById("p1");
        verify(registry, never()).unregister("custom-openai");
    }

    @Test
    void shouldDisableProviderAndUnregisterProviderWithModels() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);
        LlmProviderConfig existing = providerConfig("p1", "custom-openai", LlmProviderStatus.ENABLED);
        existing.setApiKeyCiphertext("cipher");
        existing.setVersion(1);
        when(providerMapper.selectById("p1")).thenReturn(existing);
        when(authContextService.getCurrentUserId()).thenReturn("u1");
        when(crypto.decrypt("cipher")).thenReturn("sk-test");

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                mock(LlmDebugSessionMapper.class),
                registry,
                crypto,
                mock(HttpLlmDebugClient.class),
                authContextService,
                new ObjectMapper()
        );

        LlmProviderResponse response = service.disableProvider("p1").getData();

        assertEquals(LlmProviderStatus.DISABLED, response.getStatus());
        verify(providerMapper).updateById(existing);
        verify(registry).unregister("custom-openai");
    }

    @Test
    void shouldEnableProviderAndRegisterEnabledModels() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmSecretCrypto crypto = mock(LlmSecretCrypto.class);
        com.example.demo.service.AuthContextService authContextService = mock(com.example.demo.service.AuthContextService.class);
        LlmProviderConfig existing = providerConfig("p1", "custom-openai", LlmProviderStatus.DISABLED);
        existing.setApiKeyCiphertext("cipher");
        LlmModelConfig enabledModel = new LlmModelConfig();
        enabledModel.setProviderId("p1");
        enabledModel.setModelCode("qwen-plus-prod");
        enabledModel.setStatus(LlmProviderStatus.ENABLED);
        LlmModelConfig disabledModel = new LlmModelConfig();
        disabledModel.setProviderId("p1");
        disabledModel.setModelCode("qwen-max-disabled");
        disabledModel.setStatus(LlmProviderStatus.DISABLED);
        when(providerMapper.selectById("p1")).thenReturn(existing);
        when(modelMapper.selectAllModels()).thenReturn(java.util.List.of(enabledModel, disabledModel));
        when(authContextService.getCurrentUserId()).thenReturn("u1");
        when(crypto.decrypt("cipher")).thenReturn("sk-test");

        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                mock(LlmDebugSessionMapper.class),
                registry,
                crypto,
                mock(HttpLlmDebugClient.class),
                authContextService,
                new ObjectMapper()
        );

        LlmProviderResponse response = service.enableProvider("p1").getData();

        assertEquals(LlmProviderStatus.ENABLED, response.getStatus());
        verify(registry).register(existing);
        verify(registry).registerModel(enabledModel);
        verify(registry, never()).registerModel(disabledModel);
    }

    @Test
    void shouldRejectInvalidProviderJsonBeforePersisting() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        when(providerMapper.selectByProviderCode("custom-openai")).thenReturn(null);
        LlmProviderApplicationServiceImpl service = new LlmProviderApplicationServiceImpl(
                providerMapper,
                mock(LlmModelConfigMapper.class),
                mock(LlmDebugSessionMapper.class),
                mock(LlmProviderRegistry.class),
                mock(LlmSecretCrypto.class),
                mock(HttpLlmDebugClient.class),
                mock(com.example.demo.service.AuthContextService.class),
                new ObjectMapper()
        );
        CreateLlmProviderRequest request = new CreateLlmProviderRequest();
        request.setProviderCode("custom-openai");
        request.setProviderName("自定义 OpenAI");
        request.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        request.setAuthType(LlmAuthType.BEARER);
        request.setEndpointUrl("https://example.com/v1/chat/completions");
        request.setApiKey("sk-test");
        request.setDefaultModel("qwen-plus");
        request.setResponseMappingJson("{bad-json");

        BusinessException error = assertThrows(BusinessException.class, () -> service.createProvider(request));

        assertEquals(400, error.getCode());
        verify(providerMapper, never()).insert(org.mockito.ArgumentMatchers.<LlmProviderConfig>any());
    }

    private LlmProviderConfig providerConfig(String id, String providerCode, LlmProviderStatus status) {
        LlmProviderConfig config = new LlmProviderConfig();
        config.setId(id);
        config.setProviderCode(providerCode);
        config.setProviderName("自定义 OpenAI");
        config.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        config.setAuthType(LlmAuthType.BEARER);
        config.setEndpointUrl("https://example.com/v1/chat/completions");
        config.setDefaultModel("qwen-plus");
        config.setConnectTimeoutMs(5000);
        config.setReadTimeoutMs(30000);
        config.setStatus(status);
        return config;
    }
}






