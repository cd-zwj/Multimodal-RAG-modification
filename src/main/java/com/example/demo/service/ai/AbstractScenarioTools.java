package com.example.demo.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

abstract class AbstractScenarioTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final String userId;
    private final Set<String> permissions;
    protected final Map<String, Object> bizContext;

    AbstractScenarioTools(String userId, Set<String> permissions, Map<String, Object> bizContext) {
        this.userId = userId;
        this.permissions = Set.copyOf(permissions);
        this.bizContext = bizContext == null ? Map.of() : Map.copyOf(bizContext);
    }

    protected void requirePermission(String permissionCode) {
        if (!permissions.contains(permissionCode)) {
            throw new ScenarioPermissionException("当前用户无权调用工具: " + permissionCode);
        }
    }

    protected String contextValue(String key) {
        Object value = bizContext.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    protected String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
