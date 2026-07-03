package com.example.demo.model.dto.llm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateLlmModelRequest {

    @NotBlank(message = "Provider ID 不能为空")
    @Size(max = 36, message = "Provider ID 长度不能超过36个字符")
    private String providerId;

    @NotBlank(message = "展示名称不能为空")
    @Size(max = 128, message = "展示名称长度不能超过128个字符")
    private String displayName;

    @NotBlank(message = "远端模型名不能为空")
    @Size(max = 128, message = "远端模型名长度不能超过128个字符")
    private String remoteModelName;

    @Size(max = 10000, message = "默认参数长度不能超过10000个字符")
    private String defaultParamsJson;

    @Size(max = 4000, message = "能力标签长度不能超过4000个字符")
    private String capabilitiesJson;

    @NotNull(message = "排序不能为空")
    @Min(value = 0, message = "排序不能小于0")
    @Max(value = 999999, message = "排序不能超过999999")
    private Integer sortOrder = 0;
}
