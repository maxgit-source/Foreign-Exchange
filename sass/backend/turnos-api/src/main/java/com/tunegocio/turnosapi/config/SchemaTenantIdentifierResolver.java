package com.tunegocio.turnosapi.config;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Indica a Hibernate qué schema debe usar para las entidades tenant-scoped.
 */
@Component
public class SchemaTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
