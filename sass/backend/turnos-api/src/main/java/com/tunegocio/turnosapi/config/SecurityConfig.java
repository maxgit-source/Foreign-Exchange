package com.tunegocio.turnosapi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter  jwtAuthenticationFilter;
    private final AuthenticationProvider   authenticationProvider;
    private final CorsConfig               corsConfig;
    private final RateLimitFilter          rateLimitFilter;
    private final TenantRequestContextFilter tenantRequestContextFilter;
    private final ApiKeyAuthFilter         apiKeyAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/staff/invitaciones/aceptar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/staff/invitaciones/aceptar").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/templates", "/api/templates/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                // Todos los custom filters se anclan a UsernamePasswordAuthenticationFilter
                // porque es un filtro CONOCIDO en el registro de Spring Security.
                // El orden de ejecución es el orden en que se agregan:
                // 1. rateLimitFilter → 2. tenantRequestContextFilter → 3. jwtAuthenticationFilter
                // → 4. apiKeyAuthFilter → (UsernamePasswordAuthenticationFilter nunca actúa en modo stateless)
                .addFilterBefore(rateLimitFilter,             UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(tenantRequestContextFilter,  UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter,     UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter,            UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_OWNER
                ROLE_OWNER > ROLE_STAFF
                ROLE_STAFF > ROLE_CLIENT
                """);
    }

    @Bean
    public DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
