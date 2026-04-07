package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
public class TenantSchemaBootstrap implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final SchemaService schemaService;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findAll().forEach(tenant -> schemaService.ensureSchemaForTenant(tenant.getSlug()));
    }
}
