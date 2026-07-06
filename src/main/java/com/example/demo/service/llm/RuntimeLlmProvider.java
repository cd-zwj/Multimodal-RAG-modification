package com.example.demo.service.llm;

import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmProtocolType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class RuntimeLlmProvider {

    String id;
    String providerCode;
    String providerName;
    LlmProtocolType protocolType;
    LlmAuthType authType;
    String endpointUrl;
    String apiKey;
    String apiKeyCiphertext;
    String defaultModel;
    String defaultHeadersJson;
    String requestTemplateJson;
    String responseMappingJson;
    String streamConfigJson;
    String defaultParamsJson;
    String capabilitiesJson;
    Integer connectTimeoutMs;
    Integer readTimeoutMs;
    Integer version;

    public RuntimeLlmProvider withModel(RuntimeLlmModel model) {
        return RuntimeLlmProvider.builder()
                .id(id)
                .providerCode(providerCode)
                .providerName(providerName)
                .protocolType(protocolType)
                .authType(authType)
                .endpointUrl(endpointUrl)
                .apiKey(apiKey)
                .apiKeyCiphertext(apiKeyCiphertext)
                .defaultModel(model.getRemoteModelName())
                .defaultHeadersJson(defaultHeadersJson)
                .requestTemplateJson(requestTemplateJson)
                .responseMappingJson(responseMappingJson)
                .streamConfigJson(streamConfigJson)
                .defaultParamsJson(model.getDefaultParamsJson())
                .capabilitiesJson(model.getCapabilitiesJson())
                .connectTimeoutMs(connectTimeoutMs)
                .readTimeoutMs(readTimeoutMs)
                .version(version)
                .build();
    }
}
