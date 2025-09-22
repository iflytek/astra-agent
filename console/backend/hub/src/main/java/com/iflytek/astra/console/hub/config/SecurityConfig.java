package com.iflytek.astra.console.hub.config;

import com.iflytek.astra.console.commons.config.JwtClaimsFilter;
import com.iflytek.astra.console.hub.config.security.RestfulAccessDeniedHandler;
import com.iflytek.astra.console.hub.config.security.RestfulAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtClaimsFilter jwtClaimsFilter;
    private final RestfulAuthenticationEntryPoint restfulAuthenticationEntryPoint;
    private final RestfulAccessDeniedHandler restfulAccessDeniedHandler;

    @Bean
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http
                        .authorizeHttpRequests(authorize -> authorize
                                        .requestMatchers(
                                                        WebMvcConfig.NO_AUTH_REQUIRED_APIS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated() // Other interfaces require authentication
                        )
                        // Enable OAuth2 resource server support with JWT format tokens
                        .oauth2ResourceServer(oauth2 -> oauth2
                                        .jwt(Customizer.withDefaults()))
                        .exceptionHandling(exceptions -> exceptions
                                        .authenticationEntryPoint(restfulAuthenticationEntryPoint)
                                        .accessDeniedHandler(restfulAccessDeniedHandler))
                        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                        // Disable CSRF - JWT-based frontend-backend separation projects don't need CSRF protection
                        .csrf(AbstractHttpConfigurer::disable)
                        // Configure stateless session
                        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .formLogin(AbstractHttpConfigurer::disable)
                        .httpBasic(AbstractHttpConfigurer::disable)

        ;

        // Add custom Filter to put user uid into HttpServletRequest
        http.addFilterAfter(jwtClaimsFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    // Configure CORS to allow your frontend application to access across domains
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow your frontend domain to access, e.g. "http://localhost:3000"
        // configuration.setAllowedOrigins(List.of("http://localhost:3000",
        // "https://your-frontend-domain.com"));
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
