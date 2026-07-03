package com.example.demo.model.llm;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_model_config")
public class LlmModelConfig {

    @TableId(type = IdType.INPUT)
    private String id;

    private String providerId;

    private String modelCode;

    private String displayName;

    private String remoteModelName;

    private String defaultParamsJson;

    private String capabilitiesJson;

    private LlmProviderStatus status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
