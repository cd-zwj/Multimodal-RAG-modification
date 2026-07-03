package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.LlmDebugSessionMapper;
import com.example.demo.mapper.LlmModelConfigMapper;
import com.example.demo.mapper.LlmProviderConfigMapper;
import com.example.demo.model.dto.ApiResponse;
import com.example.demo.model.dto.llm.CreateLlmModelRequest;
import com.example.demo.model.dto.llm.CreateLlmProviderRequest;
import com.example.demo.model.dto.llm.LlmDebugRequest;
import com.example.demo.model.dto.llm.LlmDebugResponse;
import com.example.demo.model.dto.llm.LlmModelResponse;
import com.example.demo.model.dto.llm.LlmProviderResponse;
import com.example.demo.model.dto.llm.UpdateLlmModelRequest;
import com.example.demo.model.dto.llm.UpdateLlmProviderRequest;
import com.example.demo.model.llm.LlmDebugSession;
import com.example.demo.model.llm.LlmModelConfig;
import com.example.demo.model.llm.LlmProviderConfig;
import com.example.demo.model.llm.LlmProviderStatus;
import com.example.demo.service.AuthContextService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LlmProviderApplicationServiceImpl implements LlmProviderApplicationService {

    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final LlmModelConfigMapper llmModelConfigMapper;
    private final LlmDebugSessionMapper llmDebugSessionMapper;
    private final LlmProviderRegistry llmProviderRegistry;
    private final LlmSecretCrypto llmSecretCrypto;
    private final HttpLlmDebugClient httpLlmDebugClient;
    private final AuthContextService authContextService;
    private final ObjectMapper objectMapper;
    private final LlmJsonConfigValidator llmJsonConfigValidator;
    private final LlmDebugRateLimiter llmDebugRateLimiter;

    @Autowired
    public LlmProviderApplicationServiceImpl(LlmProviderConfigMapper llmProviderConfigMapper,
                                             LlmModelConfigMapper llmModelConfigMapper,
                                             LlmDebugSessionMapper llmDebugSessionMapper,
                                             LlmProviderRegistry llmProviderRegistry,
                                             LlmSecretCrypto llmSecretCrypto,
                                             HttpLlmDebugClient httpLlmDebugClient,
                                             AuthContextService authContextService,
                                             ObjectMapper objectMapper,
                                             LlmJsonConfigValidator llmJsonConfigValidator,
                                             LlmDebugRateLimiter llmDebugRateLimiter) {
        this.llmProviderConfigMapper = llmProviderConfigMapper;
        this.llmModelConfigMapper = llmModelConfigMapper;
        this.llmDebugSessionMapper = llmDebugSessionMapper;
        this.llmProviderRegistry = llmProviderRegistry;
        this.llmSecretCrypto = llmSecretCrypto;
        this.httpLlmDebugClient = httpLlmDebugClient;
        this.authContextService = authContextService;
        this.objectMapper = objectMapper;
        this.llmJsonConfigValidator = llmJsonConfigValidator;
        this.llmDebugRateLimiter = llmDebugRateLimiter;
    }

    public LlmProviderApplicationServiceImpl(LlmProviderConfigMapper llmProviderConfigMapper,
                                             LlmModelConfigMapper llmModelConfigMapper,
                                             LlmDebugSessionMapper llmDebugSessionMapper,
                                             LlmProviderRegistry llmProviderRegistry,
                                             LlmSecretCrypto llmSecretCrypto,
                                             HttpLlmDebugClient httpLlmDebugClient,
                                             AuthContextService authContextService,
                                             ObjectMapper objectMapper) {
        this(
                llmProviderConfigMapper,
                llmModelConfigMapper,
                llmDebugSessionMapper,
                llmProviderRegistry,
                llmSecretCrypto,
                httpLlmDebugClient,
                authContextService,
                objectMapper,
                new LlmJsonConfigValidator(objectMapper),
                new LlmDebugRateLimiter()
        );
    }

    @Override
    public ApiResponse<LlmProviderResponse> createProvider(CreateLlmProviderRequest request) {
        validateProviderCodeNotExists(request.getProviderCode());
        llmJsonConfigValidator.validateProviderConfig(
                request.getDefaultHeadersJson(),
                request.getRequestTemplateJson(),
                request.getResponseMappingJson(),
                request.getStreamConfigJson()
        );

        LlmProviderConfig config = new LlmProviderConfig();
        config.setId(UUID.randomUUID().toString());
        config.setProviderCode(request.getProviderCode().trim());
        config.setProviderName(request.getProviderName().trim());
        config.setProtocolType(request.getProtocolType());
        config.setAuthType(request.getAuthType());
        config.setEndpointUrl(request.getEndpointUrl().trim());
        config.setApiKeyCiphertext(llmSecretCrypto.encrypt(request.getApiKey().trim()));
        config.setDefaultModel(request.getDefaultModel().trim());
        config.setDefaultHeadersJson(request.getDefaultHeadersJson());
        config.setRequestTemplateJson(request.getRequestTemplateJson());
        config.setResponseMappingJson(request.getResponseMappingJson());
        config.setStreamConfigJson(request.getStreamConfigJson());
        config.setConnectTimeoutMs(request.getConnectTimeoutMs());
        config.setReadTimeoutMs(request.getReadTimeoutMs());
        config.setVersion(1);
        config.setRemark(request.getRemark());
        config.setStatus(LlmProviderStatus.ENABLED);
        config.setCreatedBy(authContextService.getCurrentUserId());
        config.setUpdatedBy(authContextService.getCurrentUserId());

        llmProviderConfigMapper.insert(config);
        llmProviderRegistry.register(config);

        return ApiResponse.success(toResponse(config, request.getApiKey().trim()));
    }

    @Override
    public ApiResponse<LlmProviderResponse> updateProvider(String id, UpdateLlmProviderRequest request) {
        LlmProviderConfig config = requireProvider(id);
        llmJsonConfigValidator.validateProviderConfig(
                request.getDefaultHeadersJson(),
                request.getRequestTemplateJson(),
                request.getResponseMappingJson(),
                request.getStreamConfigJson()
        );
        config.setProviderName(request.getProviderName().trim());
        config.setProtocolType(request.getProtocolType());
        config.setAuthType(request.getAuthType());
        config.setEndpointUrl(request.getEndpointUrl().trim());
        config.setDefaultModel(request.getDefaultModel().trim());
        config.setDefaultHeadersJson(request.getDefaultHeadersJson());
        config.setRequestTemplateJson(request.getRequestTemplateJson());
        config.setResponseMappingJson(request.getResponseMappingJson());
        config.setStreamConfigJson(request.getStreamConfigJson());
        config.setConnectTimeoutMs(request.getConnectTimeoutMs());
        config.setReadTimeoutMs(request.getReadTimeoutMs());
        config.setRemark(request.getRemark());
        config.setUpdatedBy(authContextService.getCurrentUserId());
        config.setVersion(config.getVersion() == null ? 1 : config.getVersion() + 1);

        String plainApiKey = null;
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            plainApiKey = request.getApiKey().trim();
            config.setApiKeyCiphertext(llmSecretCrypto.encrypt(plainApiKey));
        } else if (config.getApiKeyCiphertext() != null) {
            plainApiKey = llmSecretCrypto.decrypt(config.getApiKeyCiphertext());
        }

        llmProviderConfigMapper.updateById(config);
        if (config.getStatus() == LlmProviderStatus.ENABLED) {
            llmProviderRegistry.register(config);
        } else {
            llmProviderRegistry.unregister(config.getProviderCode());
        }

        return ApiResponse.success(toResponse(config, plainApiKey));
    }

    @Override
    public ApiResponse<Void> deleteProvider(String id) {
        LlmProviderConfig config = requireProvider(id);
        for (LlmModelConfig model : llmModelConfigMapper.selectAllModels()) {
            if (id.equals(model.getProviderId())) {
                throw new BusinessException(409, "该 Provider 下还有模型，请先停用或删除模型后再删除 Provider");
            }
        }
        llmProviderConfigMapper.deleteById(id);
        llmProviderRegistry.unregister(config.getProviderCode());
        return ApiResponse.success("删除成功");
    }

    @Override
    public ApiResponse<LlmProviderResponse> enableProvider(String id) {
        LlmProviderConfig config = requireProvider(id);
        config.setStatus(LlmProviderStatus.ENABLED);
        config.setUpdatedBy(authContextService.getCurrentUserId());
        config.setVersion(config.getVersion() == null ? 1 : config.getVersion() + 1);
        llmProviderConfigMapper.updateById(config);
        llmProviderRegistry.register(config);
        for (LlmModelConfig model : llmModelConfigMapper.selectAllModels()) {
            if (id.equals(model.getProviderId()) && model.getStatus() == LlmProviderStatus.ENABLED) {
                llmProviderRegistry.registerModel(model);
            }
        }
        String plainApiKey = config.getApiKeyCiphertext() == null ? null : llmSecretCrypto.decrypt(config.getApiKeyCiphertext());
        return ApiResponse.success(toResponse(config, plainApiKey));
    }

    @Override
    public ApiResponse<LlmProviderResponse> disableProvider(String id) {
        LlmProviderConfig config = requireProvider(id);
        config.setStatus(LlmProviderStatus.DISABLED);
        config.setUpdatedBy(authContextService.getCurrentUserId());
        config.setVersion(config.getVersion() == null ? 1 : config.getVersion() + 1);
        llmProviderConfigMapper.updateById(config);
        llmProviderRegistry.unregister(config.getProviderCode());
        String plainApiKey = config.getApiKeyCiphertext() == null ? null : llmSecretCrypto.decrypt(config.getApiKeyCiphertext());
        return ApiResponse.success(toResponse(config, plainApiKey));
    }

    @Override
    public ApiResponse<List<LlmProviderResponse>> listProviders(boolean includeDisabled) {
        List<LlmProviderConfig> providers = includeDisabled
                ? llmProviderConfigMapper.selectAllProviders()
                : llmProviderConfigMapper.selectEnabledProviders();
        List<LlmProviderResponse> responses = providers
                .stream()
                .map(config -> toResponse(config, llmSecretCrypto.decrypt(config.getApiKeyCiphertext())))
                .toList();
        return ApiResponse.success(responses);
    }

    @Override
    public ApiResponse<LlmModelResponse> createModel(CreateLlmModelRequest request) {
        LlmProviderConfig provider = requireProvider(request.getProviderId());
        validateModelCodeNotExists(request.getModelCode());
        llmJsonConfigValidator.validateModelConfig(request.getDefaultParamsJson(), request.getCapabilitiesJson());

        LlmModelConfig model = new LlmModelConfig();
        model.setId(UUID.randomUUID().toString());
        model.setProviderId(provider.getId());
        model.setModelCode(request.getModelCode().trim());
        model.setDisplayName(request.getDisplayName().trim());
        model.setRemoteModelName(request.getRemoteModelName().trim());
        model.setDefaultParamsJson(request.getDefaultParamsJson());
        model.setCapabilitiesJson(request.getCapabilitiesJson());
        model.setStatus(LlmProviderStatus.ENABLED);
        model.setSortOrder(request.getSortOrder());

        llmModelConfigMapper.insert(model);
        if (provider.getStatus() == LlmProviderStatus.ENABLED) {
            llmProviderRegistry.registerModel(model);
        }
        return ApiResponse.success(toModelResponse(model, provider));
    }

    @Override
    public ApiResponse<LlmModelResponse> updateModel(String id, UpdateLlmModelRequest request) {
        LlmModelConfig model = requireModel(id);
        LlmProviderConfig provider = requireProvider(request.getProviderId());
        llmJsonConfigValidator.validateModelConfig(request.getDefaultParamsJson(), request.getCapabilitiesJson());
        model.setProviderId(provider.getId());
        model.setDisplayName(request.getDisplayName().trim());
        model.setRemoteModelName(request.getRemoteModelName().trim());
        model.setDefaultParamsJson(request.getDefaultParamsJson());
        model.setCapabilitiesJson(request.getCapabilitiesJson());
        model.setSortOrder(request.getSortOrder());

        llmModelConfigMapper.updateById(model);
        if (model.getStatus() == LlmProviderStatus.ENABLED && provider.getStatus() == LlmProviderStatus.ENABLED) {
            llmProviderRegistry.registerModel(model);
        } else {
            llmProviderRegistry.unregisterModel(model.getModelCode());
        }
        return ApiResponse.success(toModelResponse(model, provider));
    }

    @Override
    public ApiResponse<Void> deleteModel(String id) {
        LlmModelConfig model = requireModel(id);
        llmModelConfigMapper.deleteById(id);
        llmProviderRegistry.unregisterModel(model.getModelCode());
        return ApiResponse.success("删除成功");
    }

    @Override
    public ApiResponse<LlmModelResponse> enableModel(String id) {
        LlmModelConfig model = requireModel(id);
        LlmProviderConfig provider = requireProvider(model.getProviderId());
        model.setStatus(LlmProviderStatus.ENABLED);
        llmModelConfigMapper.updateById(model);
        if (provider.getStatus() == LlmProviderStatus.ENABLED) {
            llmProviderRegistry.registerModel(model);
        }
        return ApiResponse.success(toModelResponse(model, provider));
    }

    @Override
    public ApiResponse<LlmModelResponse> disableModel(String id) {
        LlmModelConfig model = requireModel(id);
        LlmProviderConfig provider = requireProvider(model.getProviderId());
        model.setStatus(LlmProviderStatus.DISABLED);
        llmModelConfigMapper.updateById(model);
        llmProviderRegistry.unregisterModel(model.getModelCode());
        return ApiResponse.success(toModelResponse(model, provider));
    }

    @Override
    public ApiResponse<List<LlmModelResponse>> listModels(boolean includeDisabled) {
        Map<String, LlmProviderConfig> providersById = (includeDisabled
                ? llmProviderConfigMapper.selectAllProviders()
                : llmProviderConfigMapper.selectEnabledProviders())
                .stream()
                .collect(Collectors.toMap(LlmProviderConfig::getId, Function.identity(), (left, right) -> left));
        List<LlmModelResponse> responses = llmModelConfigMapper.selectAllModels()
                .stream()
                .filter(model -> providersById.containsKey(model.getProviderId()))
                .filter(model -> includeDisabled || model.getStatus() == LlmProviderStatus.ENABLED)
                .map(model -> toModelResponse(model, providersById.get(model.getProviderId())))
                .toList();
        return ApiResponse.success(responses);
    }

    @Override
    public ApiResponse<LlmDebugResponse> debug(LlmDebugRequest request) {
        llmDebugRateLimiter.check(authContextService.getCurrentUserId(), resolveDebugProviderKey(request));
        boolean persistedProvider = request.getProviderCode() != null && !request.getProviderCode().isBlank();
        RuntimeLlmProvider provider = persistedProvider
                ? llmProviderRegistry.getRequired(request.getProviderCode().trim())
                : buildTemporaryProvider(request.getProviderConfig());
        LlmDebugResponse response = httpLlmDebugClient.debug(provider, request);
        if (persistedProvider) {
            persistDebugSession(provider, request, response);
        }
        return ApiResponse.success(response);
    }

    @Override
    public Flux<ServerSentEvent<String>> debugStream(LlmDebugRequest request) {
        request.setStream(true);
        ApiResponse<LlmDebugResponse> result = debug(request);
        LlmDebugResponse response = result.getData();
        List<ServerSentEvent<String>> events = new ArrayList<>();
        for (Map<String, Object> streamEvent : response.getStreamEvents()) {
            events.add(sse("chunk", writeValueSafely(streamEvent)));
        }
        events.add(sse("summary", writeValueSafely(response)));
        events.add(sse("done", "{}"));
        return Flux.fromIterable(events);
    }

    private LlmProviderConfig requireProvider(String id) {
        LlmProviderConfig config = llmProviderConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "Provider 不存在");
        }
        return config;
    }

    private LlmModelConfig requireModel(String id) {
        LlmModelConfig model = llmModelConfigMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(404, "模型不存在");
        }
        return model;
    }

    private RuntimeLlmProvider buildTemporaryProvider(CreateLlmProviderRequest request) {
        if (request == null) {
            throw new BusinessException(400, "必须选择已入库 Provider 或提供临时 Provider 配置");
        }
        llmJsonConfigValidator.validateProviderConfig(
                request.getDefaultHeadersJson(),
                request.getRequestTemplateJson(),
                request.getResponseMappingJson(),
                request.getStreamConfigJson()
        );
        return RuntimeLlmProvider.builder()
                .id(null)
                .providerCode(trimToNull(request.getProviderCode()))
                .providerName(trimToNull(request.getProviderName()))
                .protocolType(request.getProtocolType())
                .authType(request.getAuthType())
                .endpointUrl(request.getEndpointUrl().trim())
                .apiKey(request.getApiKey().trim())
                .defaultModel(request.getDefaultModel().trim())
                .defaultHeadersJson(request.getDefaultHeadersJson())
                .requestTemplateJson(request.getRequestTemplateJson())
                .responseMappingJson(request.getResponseMappingJson())
                .streamConfigJson(request.getStreamConfigJson())
                .defaultParamsJson(null)
                .capabilitiesJson(null)
                .connectTimeoutMs(request.getConnectTimeoutMs())
                .readTimeoutMs(request.getReadTimeoutMs())
                .version(0)
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateProviderCodeNotExists(String providerCode) {
        LlmProviderConfig existing = llmProviderConfigMapper.selectByProviderCode(providerCode.trim());
        if (existing != null) {
            throw new BusinessException(409, "Provider 编码已存在");
        }
    }

    private void validateModelCodeNotExists(String modelCode) {
        LlmModelConfig existing = llmModelConfigMapper.selectByModelCode(modelCode.trim());
        if (existing != null) {
            throw new BusinessException(409, "模型编码已存在");
        }
    }

    private LlmProviderResponse toResponse(LlmProviderConfig config, String plainApiKey) {
        LlmProviderResponse response = new LlmProviderResponse();
        response.setId(config.getId());
        response.setProviderCode(config.getProviderCode());
        response.setProviderName(config.getProviderName());
        response.setProtocolType(config.getProtocolType());
        response.setAuthType(config.getAuthType());
        response.setEndpointUrl(config.getEndpointUrl());
        response.setDefaultModel(config.getDefaultModel());
        response.setDefaultHeadersJson(config.getDefaultHeadersJson());
        response.setRequestTemplateJson(config.getRequestTemplateJson());
        response.setResponseMappingJson(config.getResponseMappingJson());
        response.setStreamConfigJson(config.getStreamConfigJson());
        response.setConnectTimeoutMs(config.getConnectTimeoutMs());
        response.setReadTimeoutMs(config.getReadTimeoutMs());
        response.setVersion(config.getVersion());
        response.setStatus(config.getStatus());
        response.setRemark(config.getRemark());
        response.setMaskedApiKey(plainApiKey == null ? null : llmSecretCrypto.mask(plainApiKey));
        return response;
    }

    private LlmModelResponse toModelResponse(LlmModelConfig model, LlmProviderConfig provider) {
        LlmModelResponse response = new LlmModelResponse();
        response.setId(model.getId());
        response.setProviderId(model.getProviderId());
        response.setProviderCode(provider.getProviderCode());
        response.setProviderName(provider.getProviderName());
        response.setModelCode(model.getModelCode());
        response.setDisplayName(model.getDisplayName());
        response.setRemoteModelName(model.getRemoteModelName());
        response.setDefaultParamsJson(model.getDefaultParamsJson());
        response.setCapabilitiesJson(model.getCapabilitiesJson());
        response.setStatus(model.getStatus());
        response.setSortOrder(model.getSortOrder());
        return response;
    }

    private void persistDebugSession(RuntimeLlmProvider provider,
                                     LlmDebugRequest request,
                                     LlmDebugResponse response) {
        LlmDebugSession session = new LlmDebugSession();
        session.setId(UUID.randomUUID().toString());
        session.setProviderId(provider.getId());
        session.setProviderCode(provider.getProviderCode());
        session.setDebugRequestJson(writeValueSafely(request));
        session.setResolvedRequestJson(response.getResolvedRequestJson());
        session.setMaskedHeadersJson(response.getMaskedHeadersJson());
        session.setRawResponseText(response.getRawResponseText());
        session.setParsedResponseJson(writeValueSafely(response));
        session.setHttpStatus(response.getHttpStatus());
        session.setLatencyMs(response.getLatencyMs());
        session.setSuccess(response.isSuccess());
        session.setErrorCode(response.getErrorCode());
        session.setErrorMessage(response.getErrorMessage());
        session.setCreatedBy(authContextService.getCurrentUserId());
        llmDebugSessionMapper.insert(session);
    }

    private String writeValueSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private String resolveDebugProviderKey(LlmDebugRequest request) {
        if (request.getProviderCode() != null && !request.getProviderCode().isBlank()) {
            return request.getProviderCode().trim();
        }
        if (request.getProviderConfig() != null && request.getProviderConfig().getProviderCode() != null) {
            return request.getProviderConfig().getProviderCode().trim();
        }
        return "draft";
    }

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data)
                .build();
    }
}
