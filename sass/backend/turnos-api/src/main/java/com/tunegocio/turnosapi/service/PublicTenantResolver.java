package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.config.TenantContext;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicTenantResolver {

    private final TenantRepository tenantRepository;

    public Tenant resolve(String slug, HttpServletRequest request) {
        Object tenantAttribute = request.getAttribute(TenantContext.REQUEST_TENANT_ATTRIBUTE);
        if (tenantAttribute instanceof Tenant tenant) {
            return tenant;
        }

        return tenantRepository.findBySlug(slug)
                .filter(Tenant::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado: " + slug));
    }
}
