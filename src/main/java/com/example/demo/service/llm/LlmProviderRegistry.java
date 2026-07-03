package com.example.demo.service.llm;

import com.example.demo.mapper.LlmModelConfigMapper;
import com.example.demo.mapper.LlmProviderConfigMapper;
import com.example.demo.model.llm.LlmProviderStatus;
import com.example.demo.model.llm.LlmModelConfig;
import com.example.demo.model.llm.LlmProviderConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmProviderRegistry {

    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final LlmModelConfigMapper llmModelConfigMapper;
    private final LlmSecretCrypto llmSecretCrypto;
    private final Map<String, RuntimeLlmProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, RuntimeLlmModel> models = new ConcurrentHashMap<>();

    public LlmProviderRegistry(LlmProviderConfigMapper llmProviderConfigMapper,
                               LlmModelConfigMapper llmModelConfigMapper,
                               LlmSecretCrypto llmSecretCrypto) {
        this.llmProviderConfigMapper = llmProviderConfigMapper;
        this.llmModelConfigMapper = llmModelConfigMapper;
        this.llmSecretCrypto = llmSecretCrypto;
    }

    @PostConstruct
    public void initialize() {
        reload();
    }

    public void reload() {
        providers.clear();
        models.clear();
        for (LlmProviderConfig config : llmProviderConfigMapper.selectEnabledProviders()) {
            providers.put(config.getProviderCode(), toRuntime(config));
        }
        for (LlmModelConfig model : llmModelConfigMapper.selectEnabledModels()) {
            RuntimeLlmModel runtimeModel = toRuntime(model);
            if (runtimeModel.getProviderCode() != null) {
                models.put(model.getModelCode(), runtimeModel);
            }
        }
    }

    public void register(LlmProviderConfig config) {
        if (config.getStatus() == LlmProviderStatus.ENABLED) {
            providers.put(config.getProviderCode(), toRuntime(config));
        }
    }

    public void unregister(String providerCode) {
        if (providerCode != null) {
            providers.remove(providerCode);
            models.entrySet().removeIf(entry -> providerCode.equals(entry.getValue().getProviderCode()));
        }
    }

    public void registerModel(LlmModelConfig model) {
        RuntimeLlmModel runtimeModel = toRuntime(model);
        if (runtimeModel.getProviderCode() != null && providers.containsKey(runtimeModel.getProviderCode())) {
            models.put(model.getModelCode(), runtimeModel);
        }
    }

    public void unregisterModel(String modelCode) {
        if (modelCode != null) {
            models.remove(modelCode);
        }
    }

    public RuntimeLlmProvider getRequired(String providerCode) {
        RuntimeLlmProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new IllegalArgumentException("Provider 不存在或未启用");
        }
        return provider;
    }

    public RuntimeLlmProvider getRequiredByModelCode(String modelCode) {
        RuntimeLlmModel model = models.get(modelCode);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在或未启用");
        }
        RuntimeLlmProvider provider = getRequired(model.getProviderCode());
        return provider.withModel(model);
    }

    private RuntimeLlmProvider toRuntime(LlmProviderConfig config) {
        return RuntimeLlmProvider.builder()
                .id(config.getId())
                .providerCode(config.getProviderCode())
                .providerName(config.getProviderName())
                .protocolType(config.getProtocolType())
                .authType(config.getAuthType())
                .endpointUrl(config.getEndpointUrl())
                .apiKey(llmSecretCrypto.decrypt(config.getApiKeyCiphertext()))
                .defaultModel(config.getDefaultModel())
                .defaultHeadersJson(config.getDefaultHeadersJson())
                .requestTemplateJson(config.getRequestTemplateJson())
                .responseMappingJson(config.getResponseMappingJson())
                .streamConfigJson(config.getStreamConfigJson())
                .defaultParamsJson(null)
                .capabilitiesJson(null)
                .connectTimeoutMs(config.getConnectTimeoutMs())
                .readTimeoutMs(config.getReadTimeoutMs())
                .version(config.getVersion())
                .build();
    }

    private RuntimeLlmModel toRuntime(LlmModelConfig model) {
        LlmProviderConfig provider = llmProviderConfigMapper.selectById(model.getProviderId());
        return RuntimeLlmModel.builder()
                .id(model.getId())
                .providerId(model.getProviderId())
                .providerCode(provider == null ? null : provider.getProviderCode())
                .modelCode(model.getModelCode())
                .displayName(model.getDisplayName())
                .remoteModelName(model.getRemoteModelName())
                .defaultParamsJson(model.getDefaultParamsJson())
                .capabilitiesJson(model.getCapabilitiesJson())
                .sortOrder(model.getSortOrder())
                .build();
    }
}
