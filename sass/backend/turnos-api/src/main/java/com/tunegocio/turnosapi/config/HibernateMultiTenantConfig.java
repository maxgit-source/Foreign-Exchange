package com.tunegocio.turnosapi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Hibernate para multi-tenancy por schema.
 */
@Configuration
@RequiredArgsConstructor
public class HibernateMultiTenantConfig {

    private final SchemaTenantConnectionProvider connectionProvider;
    private final SchemaTenantIdentifierResolver tenantIdentifierResolver;

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return properties -> {
            properties.put("hibernate.multiTenancy", "SCHEMA");
            properties.put("hibernate.multi_tenant_connection_provider", connectionProvider);
            properties.put("hibernate.tenant_identifier_resolver", tenantIdentifierResolver);
        };
    }
}
