package com.example.demo.service.ai;

import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;
import java.util.Set;

public class UserScenarioTools extends AbstractScenarioTools {

    private static final String PERMISSION = "ai:scenario:user";

    UserScenarioTools(String userId, Set<String> permissions, Map<String, Object> bizContext) {
        super(userId, permissions, bizContext);
    }

    @Tool(description = "获取当前登录用户可访问的账户、权益、订单或知识库筛选上下文。只能返回当前用户范围内的数据线索。")
    public String getCurrentUserScenarioSnapshot() {
        requirePermission(PERMISSION);
        return json(Map.of(
                "scope", "CURRENT_USER",
                "userId", userId,
                "requestedDocumentId", contextValue("documentId"),
                "requestedOrderId", contextValue("orderId"),
                "note", "当前原型未接入正式用户订单/权益数据服务，生产实现应在此工具内查询用户授权范围内的业务数据。"
        ));
    }
}
