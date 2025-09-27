package com.iflytek.astron.console.hub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    static final String[] NO_AUTH_REQUIRED_APIS = {
            "/health",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/workflow/copyFlow",
            "/api/model/checkModelBase",
            "/workflow/hasQaNode",
            "/workflow/version/update_channel_result",
            "/error"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // registry.addInterceptor(userInfoInterceptor)
        // .addPathPatterns("/**")
        // .excludePathPatterns(NO_AUTH_REQUIRED_APIS);
    }
}
