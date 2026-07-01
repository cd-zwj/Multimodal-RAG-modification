package com.example.demo.service.ai;

import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;
import java.util.Set;

public class MerchantScenarioTools extends AbstractScenarioTools {

    private static final String PERMISSION = "ai:scenario:merchant";

    MerchantScenarioTools(String userId, Set<String> permissions, Map<String, Object> bizContext) {
        super(userId, permissions, bizContext);
    }

    @Tool(description = "获取当前商家授权范围内的销售、订单、优惠券、会员和活动数据快照，用于经营分析和建议。")
    public String getMerchantBusinessSnapshot() {
        requirePermission(PERMISSION);
        return json(Map.of(
                "scope", "CURRENT_AUTHORIZED_MERCHANT",
                "operatorUserId", userId,
                "merchantScope", contextValue("merchantScope"),
                "dateRange", contextValue("dateRange"),
                "metric", contextValue("metric"),
                "note", "当前原型未接入正式商家销售数据服务，生产实现必须在此工具内根据当前登录用户反查商家/租户范围，不能信任模型传入的 merchantId。"
        ));
    }
}
