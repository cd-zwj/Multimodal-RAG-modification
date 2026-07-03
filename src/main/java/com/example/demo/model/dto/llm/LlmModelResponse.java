package com.example.demo.model.dto.llm;

import com.example.demo.model.llm.LlmProviderStatus;
import lombok.Data;

@Data
public class LlmModelResponse {

    private String id;

    private String providerId;

    private String providerCode;

    private String providerName;

    private String modelCode;

    private String displayName;

    private String remoteModelName;

    private String defaultParamsJson;

    private String capabilitiesJson;

    private LlmProviderStatus status;

    private Integer sortOrder;
}
