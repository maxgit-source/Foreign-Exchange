-- ============================================================
-- V4 — Turnos
-- Núcleo del sistema de agenda.
-- ============================================================

CREATE TABLE turnos (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL    REFERENCES tenants(id),
    cliente_id          BIGINT          NOT NULL    REFERENCES clientes(id),
    profesional_id      BIGINT          NOT NULL    REFERENCES usuarios(id),
    servicio_id         BIGINT          NOT NULL    REFERENCES servicios(id),
    fecha_hora_inicio   TIMESTAMP       NOT NULL,
    fecha_hora_fin      TIMESTAMP       NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    notas               TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_turno_estado CHECK (estado IN ('PENDIENTE','CONFIRMADO','COMPLETADO','CANCELADO','NO_SHOW')),
    CONSTRAINT chk_turno_fechas CHECK (fecha_hora_fin > fecha_hora_inicio)
);

COMMENT ON TABLE turnos IS 'Turnos/citas agendadas. Núcleo del sistema.';
COMMENT ON CONSTRAINT chk_turno_fechas ON turnos IS 'La fecha de fin siempre debe ser posterior al inicio';

-- Índices críticos para performance
CREATE INDEX idx_turno_tenant              ON turnos(tenant_id);
CREATE INDEX idx_turno_cliente             ON turnos(cliente_id);
CREATE INDEX idx_turno_profesional_fecha   ON turnos(profesional_id, fecha_hora_inicio);
CREATE INDEX idx_turno_fecha               ON turnos(fecha_hora_inicio);
CREATE INDEX idx_turno_estado              ON turnos(tenant_id, estado);

-- Índice parcial optimizado para el chequeo de solapamientos
-- Solo indexa turnos activos (no cancelados), que son los relevantes para el overlap check
CREATE INDEX idx_turno_overlap_check
    ON turnos(profesional_id, tenant_id, fecha_hora_inicio, fecha_hora_fin)
    WHERE estado <> 'CANCELADO';
