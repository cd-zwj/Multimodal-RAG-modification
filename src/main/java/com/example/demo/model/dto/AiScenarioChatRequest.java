package com.example.demo.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class AiScenarioChatRequest {
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

    @NotNull(message = "AI场景不能为空")
    private AiScenario scenario;

    @Size(max = 32, message = "业务上下文字段不能超过32个")
    private Map<String, Object> bizContext;

    public MultiTurnChatRequest toMultiTurnChatRequest() {
        MultiTurnChatRequest request = new MultiTurnChatRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setTurnCount(turnCount);
        request.setMessage(message);
        request.setModeHint(AgentMode.defaultIfNull(modeHint));
        request.setApprovedPlanId(approvedPlanId);
        request.setScenario(scenario);
        request.setBizContext(bizContext);
        return request;
    }
}
