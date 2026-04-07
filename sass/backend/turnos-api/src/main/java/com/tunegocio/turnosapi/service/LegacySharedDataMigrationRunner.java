package com.tunegocio.turnosapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class LegacySharedDataMigrationRunner implements ApplicationRunner {

    private final LegacySharedDataMigrationService migrationService;

    @Value("${app.tenancy.migrate-legacy-public-data:true}")
    private boolean migrateLegacyPublicData;

    @Override
    public void run(ApplicationArguments args) {
        if (migrateLegacyPublicData) {
            migrationService.migrateIfNeeded();
        }
    }
}
