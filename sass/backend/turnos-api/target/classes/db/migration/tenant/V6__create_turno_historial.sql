CREATE TABLE IF NOT EXISTS turno_historial (
    id              BIGSERIAL       PRIMARY KEY,
    turno_id         BIGINT          NOT NULL REFERENCES turnos(id) ON DELETE CASCADE,
    estado_anterior  VARCHAR(20),
    estado_nuevo     VARCHAR(20)     NOT NULL,
    usuario_id       BIGINT,
    fecha_anterior   TIMESTAMPTZ,
    fecha_nueva      TIMESTAMPTZ,
    notas            TEXT,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_turno_historial_estado_anterior CHECK (
        estado_anterior IS NULL OR estado_anterior IN ('PENDIENTE', 'CONFIRMADO', 'COMPLETADO', 'CANCELADO', 'NO_SHOW')
    ),
    CONSTRAINT chk_turno_historial_estado_nuevo CHECK (
        estado_nuevo IN ('PENDIENTE', 'CONFIRMADO', 'COMPLETADO', 'CANCELADO', 'NO_SHOW')
    )
);

CREATE INDEX IF NOT EXISTS idx_turno_historial_turno_created
    ON turno_historial(turno_id, created_at DESC);
