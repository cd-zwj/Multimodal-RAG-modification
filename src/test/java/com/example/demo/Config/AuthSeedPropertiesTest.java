package com.example.demo.Config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSeedPropertiesTest {

    @Test
    void shouldAllowUsersToListModelsButNotDebugOrManageProviders() {
        assertTrue(AuthSeedProperties.USER_PERMISSIONS.contains("llm:model:list"));
        assertFalse(AuthSeedProperties.USER_PERMISSIONS.contains("llm:debug"));
        assertFalse(AuthSeedProperties.USER_PERMISSIONS.contains("llm:provider:create"));
    }

    @Test
    void shouldKeepDebugAndProviderManagementAdminOnly() {
        assertTrue(AuthSeedProperties.ADMIN_EXTRA_PERMISSIONS.contains("llm:debug"));
        assertTrue(AuthSeedProperties.ADMIN_EXTRA_PERMISSIONS.contains("llm:provider:create"));
    }
}
