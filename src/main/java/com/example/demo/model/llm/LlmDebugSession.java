package com.example.demo.model.llm;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_debug_session")
public class LlmDebugSession {

    @TableId(type = IdType.INPUT)
    private String id;

    private String providerId;

    private String providerCode;

    private String debugRequestJson;

    private String resolvedRequestJson;

    private String maskedHeadersJson;

    private String rawResponseText;

    private String parsedResponseJson;

    private Integer httpStatus;

    private Long latencyMs;

    private Boolean success;

    private String errorCode;

    private String errorMessage;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
