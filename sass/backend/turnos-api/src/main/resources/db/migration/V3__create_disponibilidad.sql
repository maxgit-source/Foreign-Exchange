-- ============================================================
-- V3 — Disponibilidad de Profesionales
-- Horarios semanales recurrentes por profesional.
-- ============================================================

CREATE TABLE disponibilidad_profesional (
    id              BIGSERIAL       PRIMARY KEY,
    profesional_id  BIGINT          NOT NULL    REFERENCES usuarios(id),
    tenant_id       BIGINT          NOT NULL    REFERENCES tenants(id),
    dia             VARCHAR(15)     NOT NULL,
    hora_inicio     TIME            NOT NULL,
    hora_fin        TIME            NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_disponibilidad_dia  CHECK (dia IN ('LUNES','MARTES','MIERCOLES','JUEVES','VIERNES','SABADO','DOMINGO')),
    CONSTRAINT chk_disponibilidad_hora CHECK (hora_fin > hora_inicio)
);

COMMENT ON TABLE  disponibilidad_profesional IS 'Horarios semanales recurrentes de cada profesional';
COMMENT ON COLUMN disponibilidad_profesional.dia IS 'Día de la semana. Un profesional puede tener múltiples bloques por día (mañana + tarde)';

CREATE INDEX idx_disponibilidad_profesional ON disponibilidad_profesional(profesional_id, tenant_id, dia, activo);
