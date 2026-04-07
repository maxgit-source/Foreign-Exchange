package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final DataSource dataSource;

    @Value("${app.tenancy.tenant-migration-location:classpath:db/migration/tenant}")
    private String tenantMigrationLocation;

    public void ensureSchemaForTenant(String tenantSlug) {
        String schema = TenantContext.schemaForSlug(tenantSlug);
        createSchema(schema);
        migrateSchema(schema);
    }

    public void dropSchemaQuietly(String tenantSlug) {
        String schema = TenantContext.schemaForSlug(tenantSlug);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } catch (SQLException ex) {
            log.warn("No se pudo limpiar schema '{}' tras error de registro: {}", schema, ex.getMessage());
        }
    }

    private void createSchema(String schema) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo crear el schema del tenant: " + schema, ex);
        }
    }

    private void migrateSchema(String schema) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .locations(tenantMigrationLocation)
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
        log.info("Schema '{}' migrado correctamente", schema);
    }
}
