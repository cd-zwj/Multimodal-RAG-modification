package com.example.demo.service.ai;

import com.example.demo.Config.DateTimeTools;
import com.example.demo.model.dto.AiScenario;
import com.example.demo.service.AuthPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiScenarioToolProvider {

    private final DateTimeTools dateTimeTools;
    private final AuthPermissionService authPermissionService;

    public Object[] resolveTools(String userId, AiScenario scenario, Map<String, Object> bizContext) {
        AiScenario resolvedScenario = scenario != null ? scenario : AiScenario.GENERAL_CHAT;
        Set<String> permissions = permissionSet(userId);
        List<Object> tools = new ArrayList<>();
        tools.add(dateTimeTools);

        if (canUse(permissions, "ai:scenario:user")
                && (resolvedScenario == AiScenario.USER_ACCOUNT_SUPPORT
                || resolvedScenario == AiScenario.USER_DOCUMENT_QA
                || resolvedScenario == AiScenario.GENERAL_CHAT)) {
            tools.add(new UserScenarioTools(userId, permissions, bizContext));
        }
        if (canUse(permissions, "ai:scenario:merchant")
                && (resolvedScenario == AiScenario.MERCHANT_SALES_ANALYSIS
                || resolvedScenario == AiScenario.MERCHANT_COUPON_OPTIMIZATION)) {
            tools.add(new MerchantScenarioTools(userId, permissions, bizContext));
        }
        if (canUse(permissions, "ai:scenario:admin")
                && (resolvedScenario == AiScenario.ADMIN_OPERATION_OVERVIEW
                || resolvedScenario == AiScenario.ADMIN_RISK_AUDIT)) {
            tools.add(new AdminScenarioTools(userId, permissions, bizContext));
        }
        return tools.toArray();
    }

    public void requireScenarioAccess(String userId, AiScenario scenario) {
        AiScenario resolvedScenario = scenario != null ? scenario : AiScenario.GENERAL_CHAT;
        Set<String> permissions = permissionSet(userId);
        boolean allowed = resolvedScenario.getRequiredPermissions().stream()
                .allMatch(permissions::contains);
        if (!allowed) {
            throw new ScenarioPermissionException("当前用户无权使用 AI 场景: " + resolvedScenario.name());
        }
    }

    private Set<String> permissionSet(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(authPermissionService.getPermissionList(userId, ""));
    }

    private boolean canUse(Set<String> permissions, String permissionCode) {
        return permissions.contains(permissionCode);
    }
}
