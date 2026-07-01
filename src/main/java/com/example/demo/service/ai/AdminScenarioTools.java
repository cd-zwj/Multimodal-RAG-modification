package com.example.demo.service.ai;

import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;
import java.util.Set;

public class AdminScenarioTools extends AbstractScenarioTools {

    private static final String PERMISSION = "ai:scenario:admin";

    AdminScenarioTools(String userId, Set<String> permissions, Map<String, Object> bizContext) {
        super(userId, permissions, bizContext);
    }

    @Tool(description = "获取平台管理员授权范围内的租户、商家、用户、交易、权限和风险审计数据快照。")
    public String getAdminOperationSnapshot() {
        requirePermission(PERMISSION);
        return json(Map.of(
                "scope", "PLATFORM_ADMIN",
                "operatorUserId", userId,
                "tenantScope", contextValue("tenantScope"),
                "dateRange", contextValue("dateRange"),
                "riskType", contextValue("riskType"),
                "note", "当前原型未接入正式平台运营/风控数据服务，生产实现必须在此工具内校验管理员权限和租户边界。"
        ));
    }
}
