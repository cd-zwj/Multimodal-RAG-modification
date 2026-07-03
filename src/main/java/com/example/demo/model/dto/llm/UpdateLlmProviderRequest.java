package com.example.demo.model.dto.llm;

import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmProtocolType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateLlmProviderRequest {

    @NotBlank(message = "Provider 名称不能为空")
    @Size(max = 128, message = "Provider 名称长度不能超过128个字符")
    private String providerName;

    @NotNull(message = "协议类型不能为空")
    private LlmProtocolType protocolType;

    @NotNull(message = "鉴权类型不能为空")
    private LlmAuthType authType;

    @NotBlank(message = "接口地址不能为空")
    @Size(max = 512, message = "接口地址长度不能超过512个字符")
    private String endpointUrl;

    @Size(max = 2048, message = "API Key 长度不能超过2048个字符")
    private String apiKey;

    @NotBlank(message = "默认模型不能为空")
    @Size(max = 128, message = "默认模型长度不能超过128个字符")
    private String defaultModel;

    @Size(max = 10000, message = "默认请求头长度不能超过10000个字符")
    private String defaultHeadersJson;

    @Size(max = 20000, message = "请求模板长度不能超过20000个字符")
    private String requestTemplateJson;

    @Size(max = 10000, message = "响应映射长度不能超过10000个字符")
    private String responseMappingJson;

    @Size(max = 4000, message = "流配置长度不能超过4000个字符")
    private String streamConfigJson;

    @Min(value = 100, message = "连接超时时间不能小于100毫秒")
    @Max(value = 120000, message = "连接超时时间不能超过120000毫秒")
    private Integer connectTimeoutMs = 5000;

    @Min(value = 100, message = "读取超时时间不能小于100毫秒")
    @Max(value = 300000, message = "读取超时时间不能超过300000毫秒")
    private Integer readTimeoutMs = 30000;

    @Size(max = 1000, message = "备注长度不能超过1000")
    private String remark;
}
