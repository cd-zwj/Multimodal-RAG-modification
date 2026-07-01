package com.example.demo.model.dto;

import java.util.Set;

public enum AiScenario {
    GENERAL_CHAT(
            "通用问答",
            "保持专业、准确、简洁；当资料不足时说明边界。",
            Set.of()
    ),
    USER_ACCOUNT_SUPPORT(
            "用户账户与权益支持",
            "面向普通用户，优先解释账户、订单、优惠、文档资料相关问题；不得泄露其他用户或商家的数据。",
            Set.of("ai:scenario:user")
    ),
    USER_DOCUMENT_QA(
            "用户知识库问答",
            "面向普通用户，围绕用户可访问知识库进行问答；引用不足时明确说明缺口。",
            Set.of("ai:scenario:user")
    ),
    MERCHANT_SALES_ANALYSIS(
            "商家销售分析",
            "面向商家经营者，结合销售、订单、优惠券、会员和活动数据给出可执行经营建议；只能使用当前商家授权范围内的数据。",
            Set.of("ai:scenario:merchant")
    ),
    MERCHANT_COUPON_OPTIMIZATION(
            "商家优惠券优化",
            "面向商家经营者，分析优惠券核销、拉新、复购和成本表现，给出活动优化建议；不得跨商家读取数据。",
            Set.of("ai:scenario:merchant")
    ),
    ADMIN_OPERATION_OVERVIEW(
            "管理员运营总览",
            "面向平台管理员，汇总租户、商家、用户、交易和系统运营状态，优先指出异常和待处理事项。",
            Set.of("ai:scenario:admin")
    ),
    ADMIN_RISK_AUDIT(
            "管理员风险审计",
            "面向平台管理员，识别异常交易、权限、租户隔离和系统风险，输出证据、影响范围和处置建议。",
            Set.of("ai:scenario:admin")
    );

    private final String label;
    private final String prompt;
    private final Set<String> requiredPermissions;

    AiScenario(String label, String prompt, Set<String> requiredPermissions) {
        this.label = label;
        this.prompt = prompt;
        this.requiredPermissions = requiredPermissions;
    }

    public String getLabel() {
        return label;
    }

    public String getPrompt() {
        return prompt;
    }

    public Set<String> getRequiredPermissions() {
        return requiredPermissions;
    }
}
