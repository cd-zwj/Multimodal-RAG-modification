package com.example.demo.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 多轮对话请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiTurnChatRequest {
    @Size(max = 128, message = "用户ID长度不能超过128个字符")
    private String userId;

    @NotBlank(message = "会话ID不能为空")
    @Size(max = 128, message = "会话ID长度不能超过128个字符")
    private String sessionId;

    @Min(value = 0, message = "对话轮次不能小于0")
    @Max(value = 1000, message = "对话轮次不能超过1000")
    private Integer turnCount;

    @NotBlank(message = "问题内容不能为空")
    @Size(max = 4000, message = "问题内容不能超过4000个字符")
    private String message;

    private AgentMode modeHint = AgentMode.AUTO;

    @Size(max = 128, message = "计划ID长度不能超过128个字符")
    private String approvedPlanId;

    @Size(max = 64, message = "Provider 编码长度不能超过64个字符")
    private String providerCode;

    @Size(max = 64, message = "模型编码长度不能超过64个字符")
    private String modelCode;

    private AiScenario scenario = AiScenario.GENERAL_CHAT;

    @Size(max = 32, message = "业务上下文字段不能超过32个")
    private Map<String, Object> bizContext;
}
