package com.tunegocio.turnosapi.config;

import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ajusta el search_path de PostgreSQL por conexión para trabajar con schemas por tenant.
 */
@Component
@RequiredArgsConstructor
public class SchemaTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetSchema(connection);
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        setSearchPath(connection, tenantIdentifier);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        resetSchema(connection);
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    private void setSearchPath(Connection connection, String tenantIdentifier) throws SQLException {
        String schema = TenantContext.PUBLIC_SCHEMA.equals(tenantIdentifier)
                ? TenantContext.PUBLIC_SCHEMA
                : TenantContext.normalizeSchemaName(tenantIdentifier);

        try (Statement statement = connection.createStatement()) {
            if (TenantContext.PUBLIC_SCHEMA.equals(schema)) {
                statement.execute("SET search_path TO public");
            } else {
                statement.execute("SET search_path TO " + schema + ", public");
            }
        }
    }

    private void resetSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO public");
        }
    }
}
