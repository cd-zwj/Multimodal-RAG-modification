package com.example.demo.Config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.service.AuthSeedService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private final AuthSeedService authSeedService;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final boolean allowPublicSwagger;

    public SaTokenConfig(AuthSeedService authSeedService,
                         RateLimitInterceptor rateLimitInterceptor,
                         @Value("${app.security.allow-public-swagger:false}") boolean allowPublicSwagger) {
        this.authSeedService = authSeedService;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.allowPublicSwagger = allowPublicSwagger;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/auth/login", "/auth/register", "/auth/password/forgot/request", "/ai/multi-turn/chat");

        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()) {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Object handler) throws Exception {
                if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC) {
                    return true;
                }
                return super.preHandle(request, response, handler);
            }
        })
                .addPathPatterns("/**")
                .excludePathPatterns(authExcludePathPatterns().toArray(String[]::new));
    }

    List<String> authExcludePathPatterns() {
        List<String> patterns = new ArrayList<>(List.of(
                "/auth/login",
                "/auth/register",
                "/error",
                "/",
                "/index.html",
                "/static/**",
                "/css/**",
                "/js/**",
                "/favicon.ico"
        ));
        if (allowPublicSwagger) {
            patterns.add("/swagger-ui/**");
            patterns.add("/swagger-ui.html");
            patterns.add("/v3/api-docs/**");
        }
        return List.copyOf(patterns);
    }

    @PostConstruct
    public void seedDefaultRoles() {
        authSeedService.seedRolesAndPermissions();
    }
}
