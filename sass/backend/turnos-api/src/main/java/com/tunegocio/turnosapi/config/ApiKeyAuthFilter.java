package com.tunegocio.turnosapi.config;

import com.tunegocio.turnosapi.entity.ApiKey;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.service.ApiKeyService;
import com.tunegocio.turnosapi.service.MetricasService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que autentica requests con el header {@code X-Api-Key}.
 * Corre después del JwtAuthenticationFilter: solo actúa si no hay auth JWT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String HEADER_API_KEY = "X-Api-Key";

    private final ApiKeyService   apiKeyService;
    private final MetricasService metricasService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        // Solo actúa si no hay autenticación JWT previa
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(HEADER_API_KEY);
        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiKey apiKey = apiKeyService.validarYRegistrarUso(rawKey);

        if (apiKey == null) {
            log.debug("API key inválida o revocada en {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        Tenant tenant = apiKey.getTenant();

        // Establecer el schema del tenant para esta request
        TenantContext.setTenantSlug(tenant.getSlug());

        // Crear autenticación con role OWNER para permitir operaciones del tenant
        var auth = new UsernamePasswordAuthenticationToken(
                tenant,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Registrar métrica de uso
        metricasService.registrarUsoApiKey(tenant.getSlug());

        log.debug("Autenticado via API key: tenant={}, keyId={}", tenant.getSlug(), apiKey.getId());
        filterChain.doFilter(request, response);
    }
}
