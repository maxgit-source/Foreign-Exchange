package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.config.TenantContext;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacySharedDataMigrationService {

    private static final String DEFAULT_TIMEZONE = "America/Argentina/Buenos_Aires";
    private static final List<String> LEGACY_CORE_TABLES = List.of(
            "servicios",
            "clientes",
            "disponibilidad_profesional",
            "turnos"
    );

    private final DataSource dataSource;
    private final TenantRepository tenantRepository;

    public void migrateIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            if (!hasLegacySharedTables(connection)) {
                return;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo inspeccionar la estructura legacy de la base", ex);
        }

        tenantRepository.findAll().forEach(this::migrateTenant);
    }

    private void migrateTenant(Tenant tenant) {
        String schema = TenantContext.schemaForSlug(tenant.getSlug());

        try (Connection connection = dataSource.getConnection()) {
            if (!tenantTablesReady(connection, schema)) {
                log.warn("Se omite migracion legacy para tenant '{}' porque el schema '{}' no esta listo", tenant.getSlug(), schema);
                return;
            }

            connection.setAutoCommit(false);
            try {
                int categorias = copyCategoriasIfPresent(connection, schema, tenant.getId());
                int servicios = copyServicios(connection, schema, tenant.getId());
                int clientes = copyClientes(connection, schema, tenant.getId());
                int disponibilidades = copyDisponibilidad(connection, schema, tenant.getId());
                int bloqueos = copyFechasBloqueadasIfPresent(connection, schema, tenant.getId());
                int turnos = copyTurnos(connection, schema, tenant);
                int turnoServicios = copyTurnoServiciosIfPresent(connection, schema, tenant.getId());
                if (turnoServicios == 0) {
                    turnoServicios = seedTurnoServiciosFromLegacyColumn(connection, schema);
                }
                int historial = copyTurnoHistorialIfPresent(connection, schema, tenant);

                syncSequenceIfPresent(connection, schema, "categorias");
                syncSequenceIfPresent(connection, schema, "servicios");
                syncSequenceIfPresent(connection, schema, "clientes");
                syncSequenceIfPresent(connection, schema, "disponibilidad_profesional");
                syncSequenceIfPresent(connection, schema, "fechas_bloqueadas");
                syncSequenceIfPresent(connection, schema, "turnos");
                syncSequenceIfPresent(connection, schema, "turno_historial");

                connection.commit();

                if (categorias + servicios + clientes + disponibilidades + bloqueos + turnos + turnoServicios + historial > 0) {
                    log.info(
                            "Migracion legacy aplicada para tenant '{}': categorias={}, servicios={}, clientes={}, disponibilidad={}, bloqueos={}, turnos={}, turno_servicios={}, historial={}",
                            tenant.getSlug(),
                            categorias,
                            servicios,
                            clientes,
                            disponibilidades,
                            bloqueos,
                            turnos,
                            turnoServicios,
                            historial
                    );
                }
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo migrar la data legacy del tenant '" + tenant.getSlug() + "'", ex);
        }
    }

    private boolean hasLegacySharedTables(Connection connection) throws SQLException {
        for (String table : LEGACY_CORE_TABLES) {
            if (!tableExists(connection, "public", table)) {
                return false;
            }
        }
        return columnExists(connection, "public", "servicios", "tenant_id");
    }

    private boolean tenantTablesReady(Connection connection, String schema) throws SQLException {
        return tableExists(connection, schema, "servicios")
                && tableExists(connection, schema, "clientes")
                && tableExists(connection, schema, "disponibilidad_profesional")
                && tableExists(connection, schema, "turnos");
    }

    private int copyCategoriasIfPresent(Connection connection, String schema, Long tenantId) throws SQLException {
        if (!tableExists(connection, "public", "categorias")
                || !tableExists(connection, schema, "categorias")
                || !columnExists(connection, "public", "categorias", "tenant_id")) {
            return 0;
        }

        String createdAtExpr = columnExists(connection, "public", "categorias", "created_at")
                ? "created_at"
                : "NOW()";
        String updatedAtExpr = columnExists(connection, "public", "categorias", "updated_at")
                ? "updated_at"
                : createdAtExpr;
        String descripcionExpr = columnExists(connection, "public", "categorias", "descripcion")
                ? "descripcion"
                : "NULL";
        String ordenExpr = columnExists(connection, "public", "categorias", "orden")
                ? "COALESCE(orden, 0)"
                : "0";
        String activoExpr = columnExists(connection, "public", "categorias", "activo")
                ? "COALESCE(activo, TRUE)"
                : "TRUE";

        String sql = """
                INSERT INTO %s (id, nombre, descripcion, orden, activo, created_at, updated_at)
                SELECT
                    id,
                    nombre,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                FROM public.categorias
                WHERE tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "categorias"), descripcionExpr, ordenExpr, activoExpr, createdAtExpr, updatedAtExpr);
        return executeInsert(connection, sql, tenantId);
    }

    private int copyServicios(Connection connection, String schema, Long tenantId) throws SQLException {
        String categoriaExpr = columnExists(connection, "public", "servicios", "categoria_id")
                && tableExists(connection, schema, "categorias")
                ? "categoria_id"
                : "NULL";
        String imagenExpr = columnExists(connection, "public", "servicios", "imagen_url")
                ? "imagen_url"
                : "NULL";

        String sql = """
                INSERT INTO %s (
                    id, nombre, descripcion, duracion_minutos, precio, categoria_id, imagen_url, activo, created_at, updated_at
                )
                SELECT
                    id, nombre, descripcion, duracion_minutos, precio, %s, %s, activo, created_at, updated_at
                FROM public.servicios
                WHERE tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "servicios"), categoriaExpr, imagenExpr);
        return executeInsert(connection, sql, tenantId);
    }

    private int copyClientes(Connection connection, String schema, Long tenantId) throws SQLException {
        String sql = """
                INSERT INTO %s (id, nombre, apellido, email, telefono, notas, activo, created_at, updated_at)
                SELECT id, nombre, apellido, email, telefono, notas, activo, created_at, updated_at
                FROM public.clientes
                WHERE tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "clientes"));
        return executeInsert(connection, sql, tenantId);
    }

    private int copyDisponibilidad(Connection connection, String schema, Long tenantId) throws SQLException {
        String sql = """
                INSERT INTO %s (id, profesional_id, dia, hora_inicio, hora_fin, activo, created_at, updated_at)
                SELECT id, profesional_id, dia, hora_inicio, hora_fin, activo, created_at, updated_at
                FROM public.disponibilidad_profesional
                WHERE tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "disponibilidad_profesional"));
        return executeInsert(connection, sql, tenantId);
    }

    private int copyFechasBloqueadasIfPresent(Connection connection, String schema, Long tenantId) throws SQLException {
        if (!tableExists(connection, "public", "fechas_bloqueadas")
                || !tableExists(connection, schema, "fechas_bloqueadas")
                || !columnExists(connection, "public", "fechas_bloqueadas", "tenant_id")) {
            return 0;
        }

        String createdAtExpr = columnExists(connection, "public", "fechas_bloqueadas", "created_at")
                ? "created_at"
                : "NOW()";
        String updatedAtExpr = columnExists(connection, "public", "fechas_bloqueadas", "updated_at")
                ? "updated_at"
                : createdAtExpr;
        String profesionalExpr = columnExists(connection, "public", "fechas_bloqueadas", "profesional_id")
                ? "profesional_id"
                : "NULL";
        String motivoExpr = columnExists(connection, "public", "fechas_bloqueadas", "motivo")
                ? "motivo"
                : "NULL";

        String sql = """
                INSERT INTO %s (id, profesional_id, fecha_inicio, fecha_fin, motivo, created_at, updated_at)
                SELECT id, %s, fecha_inicio, fecha_fin, %s, %s, %s
                FROM public.fechas_bloqueadas
                WHERE tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "fechas_bloqueadas"), profesionalExpr, motivoExpr, createdAtExpr, updatedAtExpr);
        return executeInsert(connection, sql, tenantId);
    }

    private int copyTurnos(Connection connection, String schema, Tenant tenant) throws SQLException {
        String timezoneLiteral = sqlLiteral(resolveTimezone(tenant));

        String servicioExpr = columnExists(connection, "public", "turnos", "servicio_id")
                ? "servicio_id"
                : "NULL";
        String turnoPadreExpr = columnExists(connection, "public", "turnos", "turno_padre_id")
                ? "turno_padre_id"
                : "NULL";
        String recurrenteExpr = columnExists(connection, "public", "turnos", "es_recurrente")
                ? "es_recurrente"
                : "FALSE";
        String recurrenciaExpr = columnExists(connection, "public", "turnos", "recurrencia_semanas")
                ? "recurrencia_semanas"
                : "NULL";
        String inicioLocalExpr = columnExists(connection, "public", "turnos", "fecha_hora_inicio_local")
                ? "fecha_hora_inicio_local"
                : "fecha_hora_inicio";
        String finLocalExpr = columnExists(connection, "public", "turnos", "fecha_hora_fin_local")
                ? "fecha_hora_fin_local"
                : "fecha_hora_fin";
        String inicioUtcExpr = legacyInstantExpression(connection, "public", "turnos", "fecha_hora_inicio_local", "fecha_hora_inicio", timezoneLiteral);
        String finUtcExpr = legacyInstantExpression(connection, "public", "turnos", "fecha_hora_fin_local", "fecha_hora_fin", timezoneLiteral);
        String recordatorioExpr = columnExists(connection, "public", "turnos", "recordatorio_24h_enviado")
                ? "recordatorio_24h_enviado"
                : "FALSE";

        String sql = """
                INSERT INTO %s (
                    id, cliente_id, profesional_id, servicio_id, turno_padre_id, es_recurrente, recurrencia_semanas,
                    fecha_hora_inicio_local, fecha_hora_fin_local, fecha_hora_inicio, fecha_hora_fin,
                    estado, recordatorio_24h_enviado, notas, created_at, updated_at
                )
                SELECT
                    id, cliente_id, profesional_id, %s, %s, %s, %s,
                    %s, %s, %s, %s,
                    estado, %s, notas, created_at, updated_at
                FROM public.turnos
                WHERE tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(
                qualified(schema, "turnos"),
                servicioExpr,
                turnoPadreExpr,
                recurrenteExpr,
                recurrenciaExpr,
                inicioLocalExpr,
                finLocalExpr,
                inicioUtcExpr,
                finUtcExpr,
                recordatorioExpr
        );
        return executeInsert(connection, sql, tenant.getId());
    }

    private int copyTurnoServiciosIfPresent(Connection connection, String schema, Long tenantId) throws SQLException {
        if (!tableExists(connection, "public", "turno_servicios")
                || !tableExists(connection, schema, "turno_servicios")) {
            return 0;
        }

        String sql = """
                INSERT INTO %s (turno_id, servicio_id)
                SELECT ts.turno_id, ts.servicio_id
                FROM public.turno_servicios ts
                INNER JOIN public.turnos t ON t.id = ts.turno_id
                WHERE t.tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "turno_servicios"));
        return executeInsert(connection, sql, tenantId);
    }

    private int seedTurnoServiciosFromLegacyColumn(Connection connection, String schema) throws SQLException {
        if (!tableExists(connection, schema, "turno_servicios")
                || !columnExists(connection, schema, "turnos", "servicio_id")) {
            return 0;
        }

        String sql = """
                INSERT INTO %s (turno_id, servicio_id)
                SELECT id, servicio_id
                FROM %s
                WHERE servicio_id IS NOT NULL
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "turno_servicios"), qualified(schema, "turnos"));
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private int copyTurnoHistorialIfPresent(Connection connection, String schema, Tenant tenant) throws SQLException {
        if (!tableExists(connection, "public", "turno_historial")
                || !tableExists(connection, schema, "turno_historial")) {
            return 0;
        }

        String timezoneLiteral = sqlLiteral(resolveTimezone(tenant));
        String notasExpr = columnExists(connection, "public", "turno_historial", "notas")
                ? "th.notas"
                : "NULL";
        String createdAtExpr = columnExists(connection, "public", "turno_historial", "created_at")
                ? legacyInstantExpression(connection, "public", "turno_historial", null, "created_at", timezoneLiteral, "th")
                : "NOW()";
        String fechaAnteriorExpr = columnExists(connection, "public", "turno_historial", "fecha_anterior")
                ? legacyInstantExpression(connection, "public", "turno_historial", null, "fecha_anterior", timezoneLiteral, "th")
                : "NULL";
        String fechaNuevaExpr = columnExists(connection, "public", "turno_historial", "fecha_nueva")
                ? legacyInstantExpression(connection, "public", "turno_historial", null, "fecha_nueva", timezoneLiteral, "th")
                : "NULL";
        String estadoAnteriorExpr = columnExists(connection, "public", "turno_historial", "estado_anterior")
                ? "th.estado_anterior"
                : "NULL";
        String usuarioExpr = columnExists(connection, "public", "turno_historial", "usuario_id")
                ? "th.usuario_id"
                : "NULL";

        String sql = """
                INSERT INTO %s (
                    id, turno_id, estado_anterior, estado_nuevo, usuario_id, fecha_anterior, fecha_nueva, notas, created_at
                )
                SELECT
                    th.id, th.turno_id, %s, th.estado_nuevo, %s, %s, %s, %s, %s
                FROM public.turno_historial th
                INNER JOIN public.turnos t ON t.id = th.turno_id
                WHERE t.tenant_id = ?
                ON CONFLICT DO NOTHING
                """.formatted(qualified(schema, "turno_historial"), estadoAnteriorExpr, usuarioExpr, fechaAnteriorExpr, fechaNuevaExpr, notasExpr, createdAtExpr);
        return executeInsert(connection, sql, tenant.getId());
    }

    private int executeInsert(Connection connection, String sql, Long tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, tenantId);
            return statement.executeUpdate();
        }
    }

    private void syncSequenceIfPresent(Connection connection, String schema, String table) throws SQLException {
        if (!tableExists(connection, schema, table) || !sequenceExists(connection, schema, table + "_id_seq")) {
            return;
        }
        syncSequence(connection, schema, table);
    }

    private void syncSequence(Connection connection, String schema, String table) throws SQLException {
        Long maxId = maxId(connection, schema, table);
        String sql;

        if (maxId == null) {
            sql = "SELECT setval('" + schema + "." + table + "_id_seq', 1, false)";
        } else {
            sql = "SELECT setval('" + schema + "." + table + "_id_seq', " + maxId + ", true)";
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Long maxId(Connection connection, String schema, String table) throws SQLException {
        String sql = "SELECT MAX(id) FROM " + qualified(schema, table);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            long value = resultSet.getLong(1);
            return resultSet.wasNull() ? null : value;
        }
    }

    private boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean sequenceExists(Connection connection, String schema, String sequence) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.sequences
                WHERE sequence_schema = ?
                  AND sequence_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, sequence);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(Connection connection, String schema, String table, String column) throws SQLException {
        return columnDataType(connection, schema, table, column) != null;
    }

    private String columnDataType(Connection connection, String schema, String table, String column) throws SQLException {
        String sql = """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND column_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("data_type") : null;
            }
        }
    }

    private String legacyInstantExpression(Connection connection,
                                          String schema,
                                          String table,
                                          String localColumn,
                                          String mainColumn,
                                          String timezoneLiteral) throws SQLException {
        return legacyInstantExpression(connection, schema, table, localColumn, mainColumn, timezoneLiteral, null);
    }

    private String legacyInstantExpression(Connection connection,
                                          String schema,
                                          String table,
                                          String localColumn,
                                          String mainColumn,
                                          String timezoneLiteral,
                                          String alias) throws SQLException {
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";

        if (localColumn != null && columnExists(connection, schema, table, localColumn)) {
            return prefix + localColumn + " AT TIME ZONE " + timezoneLiteral;
        }

        String dataType = columnDataType(connection, schema, table, mainColumn);
        if ("timestamp with time zone".equalsIgnoreCase(dataType)) {
            return prefix + mainColumn;
        }

        return prefix + mainColumn + " AT TIME ZONE " + timezoneLiteral;
    }

    private String resolveTimezone(Tenant tenant) {
        String timezone = tenant.getTimezone();
        return timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
    }

    private String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String qualified(String schema, String table) {
        return schema + "." + table;
    }
}
