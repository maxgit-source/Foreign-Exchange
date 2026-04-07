CREATE TABLE notificaciones_log (
    id                  BIGSERIAL       PRIMARY KEY,
    tipo                VARCHAR(50)     NOT NULL,
    destinatario_email  VARCHAR(200)    NOT NULL,
    asunto              VARCHAR(500),
    estado              VARCHAR(20)     NOT NULL,
    error_detalle       TEXT,
    turno_id            BIGINT          REFERENCES turnos(id) ON DELETE SET NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_notificacion_tipo CHECK (
        tipo IN ('CONFIRMACION', 'CANCELACION', 'RECORDATORIO', 'REPROGRAMACION', 'INVITACION_STAFF', 'BIENVENIDA_STAFF', 'CONFIRMACION_PEDIDO')
    ),
    CONSTRAINT chk_notificacion_estado CHECK (
        estado IN ('ENVIADO', 'ERROR')
    )
);

CREATE INDEX idx_notificaciones_log_estado ON notificaciones_log(estado, created_at DESC);
CREATE INDEX idx_notificaciones_log_turno  ON notificaciones_log(turno_id);
