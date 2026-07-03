package com.example.demo.model.llm;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_provider_config")
public class LlmProviderConfig {

    @TableId(type = IdType.INPUT)
    private String id;

    private String providerCode;

    private String providerName;

    private LlmProtocolType protocolType;

    private LlmAuthType authType;

    private String endpointUrl;

    private String apiKeyCiphertext;

    private String defaultModel;

    private String defaultHeadersJson;

    private String requestTemplateJson;

    private String responseMappingJson;

    private String streamConfigJson;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Integer version;

    private String remark;

    private LlmProviderStatus status;

    private String createdBy;

    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
