package com.example.demo.model.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class LlmDebugRequest {

    @Size(max = 64, message = "Provider 编码长度不能超过64个字符")
    private String providerCode;

    @Valid
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private CreateLlmProviderRequest providerConfig;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "消息内容不能超过8000个字符")
    private String message;

    @Size(max = 4000, message = "系统提示词不能超过4000个字符")
    private String systemPrompt;

    private Boolean stream = false;

    private Map<String, Object> params;

    private Map<String, Object> context;

    @AssertTrue(message = "必须选择已入库 Provider 或提供临时 Provider 配置")
    public boolean isProviderSourcePresent() {
        return providerCode != null && !providerCode.isBlank() || providerConfig != null;
    }
}



