package com.example.demo.service.llm;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RuntimeLlmModel {

    String id;
    String providerId;
    String providerCode;
    String modelCode;
    String displayName;
    String remoteModelName;
    String defaultParamsJson;
    String capabilitiesJson;
    Integer sortOrder;
}
