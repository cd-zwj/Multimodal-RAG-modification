package com.example.demo.Config;

import com.example.demo.service.AuthSeedService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaTokenConfigTest {

    @Test
    void shouldRateLimitAiMultiTurnChatEndpoint() {
        AuthSeedService authSeedService = mock(AuthSeedService.class);
        RateLimitInterceptor rateLimitInterceptor = mock(RateLimitInterceptor.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration rateLimitRegistration = mock(InterceptorRegistration.class);
        InterceptorRegistration authRegistration = mock(InterceptorRegistration.class);

        when(registry.addInterceptor(rateLimitInterceptor)).thenReturn(rateLimitRegistration);
        when(registry.addInterceptor(argThat(interceptor -> interceptor != rateLimitInterceptor))).thenReturn(authRegistration);
        when(rateLimitRegistration.addPathPatterns(org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(rateLimitRegistration);
        when(authRegistration.addPathPatterns(org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(authRegistration);
        when(authRegistration.excludePathPatterns(org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(authRegistration);

        new SaTokenConfig(authSeedService, rateLimitInterceptor, false).addInterceptors(registry);

        verify(rateLimitRegistration).addPathPatterns(
                "/auth/login",
                "/auth/register",
                "/auth/password/forgot/request",
                "/ai/multi-turn/chat"
        );
    }

    @Test
    void shouldNotAllowSwaggerAnonymouslyByDefault() {
        SaTokenConfig config = new SaTokenConfig(mock(AuthSeedService.class), mock(RateLimitInterceptor.class), false);

        assertFalse(config.authExcludePathPatterns().contains("/swagger-ui/**"));
        assertFalse(config.authExcludePathPatterns().contains("/v3/api-docs/**"));
    }

    @Test
    void shouldAllowSwaggerAnonymouslyOnlyWhenExplicitlyEnabled() {
        SaTokenConfig config = new SaTokenConfig(mock(AuthSeedService.class), mock(RateLimitInterceptor.class), true);

        assertTrue(config.authExcludePathPatterns().contains("/swagger-ui/**"));
        assertTrue(config.authExcludePathPatterns().contains("/v3/api-docs/**"));
    }
}
