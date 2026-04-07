package com.tunegocio.turnosapi.config;

import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resuelve el tenant en endpoints públicos usando el slug de la URL.
 */
@Component
@RequiredArgsConstructor
public class TenantRequestContextFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String slug = extractPublicSlug(request.getRequestURI());
            if (slug != null) {
                tenantRepository.findBySlug(slug)
                        .filter(Tenant::isActivo)
                        .ifPresent(tenant -> {
                            TenantContext.setTenantSlug(tenant.getSlug());
                            request.setAttribute(TenantContext.REQUEST_TENANT_ATTRIBUTE, tenant);
                        });
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractPublicSlug(String requestUri) {
        if (requestUri == null || !requestUri.startsWith("/public/")) {
            return null;
        }

        String[] parts = requestUri.split("/");
        if (parts.length < 3) {
            return null;
        }

        return parts[2];
    }
}
