CREATE TABLE IF NOT EXISTS fechas_bloqueadas (
    id              BIGSERIAL       PRIMARY KEY,
    profesional_id  BIGINT          REFERENCES public.usuarios(id),
    fecha_inicio    DATE            NOT NULL,
    fecha_fin       DATE            NOT NULL,
    motivo          VARCHAR(200),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_fecha_bloqueada_rango CHECK (fecha_fin >= fecha_inicio)
);

CREATE INDEX IF NOT EXISTS idx_fecha_bloqueada_profesional
    ON fechas_bloqueadas(profesional_id, fecha_inicio, fecha_fin);

CREATE INDEX IF NOT EXISTS idx_fecha_bloqueada_rango
    ON fechas_bloqueadas(fecha_inicio, fecha_fin);
