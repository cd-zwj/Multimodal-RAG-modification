package com.example.demo.service.llm;

import com.example.demo.mapper.LlmDebugSessionMapper;
import com.example.demo.mapper.LlmModelConfigMapper;
import com.example.demo.mapper.LlmProviderConfigMapper;
import com.example.demo.exception.BusinessException;
import com.example.demo.model.dto.llm.CreateLlmModelRequest;
import com.example.demo.model.dto.llm.LlmModelResponse;
import com.example.demo.model.dto.llm.UpdateLlmModelRequest;
import com.example.demo.model.llm.LlmModelConfig;
import com.example.demo.model.llm.LlmProviderConfig;
import com.example.demo.model.llm.LlmProviderStatus;
import com.example.demo.service.AuthContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmModelApplicationServiceTest {

    @Test
    void shouldCreateModelPersistAndRegister() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmProviderConfig provider = provider();
        when(providerMapper.selectById("p1")).thenReturn(provider);
        when(modelMapper.selectByModelCode("qwen-plus-prod")).thenReturn(null);
        ArgumentCaptor<LlmModelConfig> modelCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        LlmProviderApplicationServiceImpl service = service(providerMapper, modelMapper, registry);
        CreateLlmModelRequest request = new CreateLlmModelRequest();
        request.setProviderId("p1");
        request.setModelCode("qwen-plus-prod");
        request.setDisplayName("Qwen Plus");
        request.setRemoteModelName("qwen-plus");
        request.setSortOrder(10);

        LlmModelResponse response = service.createModel(request).getData();

        assertEquals("qwen-plus-prod", response.getModelCode());
        assertEquals("custom-openai", response.getProviderCode());
        verify(modelMapper).insert(modelCaptor.capture());
        verify(registry).registerModel(modelCaptor.getValue());
    }

    @Test
    void shouldUpdateModelWithoutChangingModelCode() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        when(providerMapper.selectById("p1")).thenReturn(provider());
        LlmModelConfig model = new LlmModelConfig();
        model.setId("m1");
        model.setProviderId("p1");
        model.setModelCode("qwen-plus-prod");
        model.setStatus(LlmProviderStatus.ENABLED);
        when(modelMapper.selectById("m1")).thenReturn(model);
        ArgumentCaptor<LlmModelConfig> modelCaptor = ArgumentCaptor.forClass(LlmModelConfig.class);

        LlmProviderApplicationServiceImpl service = service(providerMapper, modelMapper, registry);
        UpdateLlmModelRequest request = new UpdateLlmModelRequest();
        request.setProviderId("p1");
        request.setDisplayName("Qwen Plus Latest");
        request.setRemoteModelName("qwen-plus-latest");
        request.setSortOrder(20);

        service.updateModel("m1", request);

        verify(modelMapper).updateById(modelCaptor.capture());
        assertEquals("qwen-plus-prod", modelCaptor.getValue().getModelCode());
        assertEquals("qwen-plus-latest", modelCaptor.getValue().getRemoteModelName());
        verify(registry).registerModel(modelCaptor.getValue());
    }

    @Test
    void shouldDeleteModelAndUnregisterRuntimeCache() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmModelConfig model = new LlmModelConfig();
        model.setId("m1");
        model.setModelCode("qwen-plus-prod");
        when(modelMapper.selectById("m1")).thenReturn(model);

        LlmProviderApplicationServiceImpl service = service(providerMapper, modelMapper, registry);

        service.deleteModel("m1");

        verify(modelMapper).deleteById("m1");
        verify(registry).unregisterModel("qwen-plus-prod");
    }

    @Test
    void shouldDisableModelAndUnregisterRuntimeCache() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmModelConfig model = new LlmModelConfig();
        model.setId("m1");
        model.setProviderId("p1");
        model.setModelCode("qwen-plus-prod");
        model.setStatus(LlmProviderStatus.ENABLED);
        when(modelMapper.selectById("m1")).thenReturn(model);
        when(providerMapper.selectById("p1")).thenReturn(provider());

        LlmProviderApplicationServiceImpl service = service(providerMapper, modelMapper, registry);

        LlmModelResponse response = service.disableModel("m1").getData();

        assertEquals(LlmProviderStatus.DISABLED, response.getStatus());
        verify(modelMapper).updateById(model);
        verify(registry).unregisterModel("qwen-plus-prod");
    }

    @Test
    void shouldEnableModelOnlyRegistersWhenProviderEnabled() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        LlmProviderConfig disabledProvider = provider();
        disabledProvider.setStatus(LlmProviderStatus.DISABLED);
        LlmModelConfig model = new LlmModelConfig();
        model.setId("m1");
        model.setProviderId("p1");
        model.setModelCode("qwen-plus-prod");
        model.setStatus(LlmProviderStatus.DISABLED);
        when(modelMapper.selectById("m1")).thenReturn(model);
        when(providerMapper.selectById("p1")).thenReturn(disabledProvider);

        LlmProviderApplicationServiceImpl service = service(providerMapper, modelMapper, registry);

        LlmModelResponse response = service.enableModel("m1").getData();

        assertEquals(LlmProviderStatus.ENABLED, response.getStatus());
        verify(modelMapper).updateById(model);
        verify(registry, never()).registerModel(model);
    }

    @Test
    void shouldRejectInvalidModelJsonBeforePersisting() {
        LlmProviderConfigMapper providerMapper = mock(LlmProviderConfigMapper.class);
        LlmModelConfigMapper modelMapper = mock(LlmModelConfigMapper.class);
        LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
        when(providerMapper.selectById("p1")).thenReturn(provider());
        when(modelMapper.selectByModelCode("qwen-plus-prod")).thenReturn(null);

        LlmProviderApplicationServiceImpl service = service(providerMapper, modelMapper, registry);
        CreateLlmModelRequest request = new CreateLlmModelRequest();
        request.setProviderId("p1");
        request.setModelCode("qwen-plus-prod");
        request.setDisplayName("Qwen Plus");
        request.setRemoteModelName("qwen-plus");
        request.setDefaultParamsJson("{bad-json");
        request.setSortOrder(10);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createModel(request));

        assertEquals(400, error.getCode());
        verify(modelMapper, never()).insert(org.mockito.ArgumentMatchers.<LlmModelConfig>any());
        verify(registry, never()).registerModel(org.mockito.ArgumentMatchers.<LlmModelConfig>any());
    }

    private LlmProviderApplicationServiceImpl service(LlmProviderConfigMapper providerMapper,
                                                      LlmModelConfigMapper modelMapper,
                                                      LlmProviderRegistry registry) {
        return new LlmProviderApplicationServiceImpl(
                providerMapper,
                modelMapper,
                mock(LlmDebugSessionMapper.class),
                registry,
                mock(LlmSecretCrypto.class),
                mock(HttpLlmDebugClient.class),
                mock(AuthContextService.class),
                new ObjectMapper()
        );
    }

    private LlmProviderConfig provider() {
        LlmProviderConfig provider = new LlmProviderConfig();
        provider.setId("p1");
        provider.setProviderCode("custom-openai");
        provider.setProviderName("自定义 OpenAI");
        provider.setStatus(LlmProviderStatus.ENABLED);
        return provider;
    }
}
