package com.example.demo.model.dto.llm;

import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmProtocolType;
import com.example.demo.model.llm.LlmProviderStatus;
import lombok.Data;

@Data
public class LlmProviderResponse {

    private String id;

    private String providerCode;

    private String providerName;

    private LlmProtocolType protocolType;

    private LlmAuthType authType;

    private String endpointUrl;

    private String defaultModel;

    private String defaultHeadersJson;

    private String requestTemplateJson;

    private String responseMappingJson;

    private String streamConfigJson;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Integer version;

    private LlmProviderStatus status;

    private String remark;

    private String maskedApiKey;
}
