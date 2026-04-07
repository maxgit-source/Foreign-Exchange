package com.tunegocio.turnosapi.config;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Contexto thread-local del schema activo para el request o tarea actual.
 */
public final class TenantContext {

    public static final String PUBLIC_SCHEMA = "public";
    public static final String REQUEST_TENANT_ATTRIBUTE = TenantContext.class.getName() + ".tenant";

    private static final Pattern INVALID_SCHEMA_CHARS = Pattern.compile("[^a-z0-9_]");
    private static final ThreadLocal<String> CURRENT_SCHEMA = ThreadLocal.withInitial(() -> PUBLIC_SCHEMA);

    private TenantContext() {
    }

    public static String getCurrentSchema() {
        String schema = CURRENT_SCHEMA.get();
        return schema == null || schema.isBlank() ? PUBLIC_SCHEMA : schema;
    }

    public static void setCurrentSchema(String schema) {
        CURRENT_SCHEMA.set(normalizeSchema(schema));
    }

    public static void setTenantSlug(String slug) {
        CURRENT_SCHEMA.set(schemaForSlug(slug));
    }

    public static String schemaForSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("El slug del tenant es obligatorio");
        }

        String normalized = INVALID_SCHEMA_CHARS
                .matcher(slug.toLowerCase(Locale.ROOT).replace('-', '_'))
                .replaceAll("");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("El slug del tenant no genera un schema válido");
        }

        return "tenant_" + normalized;
    }

    public static Scope openTenantSlug(String slug) {
        return openSchema(schemaForSlug(slug));
    }

    public static Scope openSchema(String schema) {
        String previous = getCurrentSchema();
        setCurrentSchema(schema);
        return () -> {
            if (PUBLIC_SCHEMA.equals(previous)) {
                CURRENT_SCHEMA.remove();
            } else {
                CURRENT_SCHEMA.set(previous);
            }
        };
    }

    public static void clear() {
        CURRENT_SCHEMA.remove();
    }

    private static String normalizeSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return PUBLIC_SCHEMA;
        }

        String normalized = INVALID_SCHEMA_CHARS
                .matcher(schema.toLowerCase(Locale.ROOT))
                .replaceAll("");

        return normalized.isBlank() ? PUBLIC_SCHEMA : normalized;
    }

    public static String normalizeSchemaName(String schema) {
        return normalizeSchema(schema);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
