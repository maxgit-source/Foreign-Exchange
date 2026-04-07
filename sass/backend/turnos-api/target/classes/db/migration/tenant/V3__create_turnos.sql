CREATE TABLE turnos (
    id                      BIGSERIAL       PRIMARY KEY,
    cliente_id              BIGINT          NOT NULL REFERENCES clientes(id),
    profesional_id          BIGINT          NOT NULL REFERENCES public.usuarios(id),
    servicio_id             BIGINT          NOT NULL REFERENCES servicios(id),
    fecha_hora_inicio       TIMESTAMP       NOT NULL,
    fecha_hora_fin          TIMESTAMP       NOT NULL,
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    recordatorio_24h_enviado BOOLEAN       NOT NULL DEFAULT FALSE,
    notas                   TEXT,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_turno_estado CHECK (
        estado IN ('PENDIENTE', 'CONFIRMADO', 'COMPLETADO', 'CANCELADO', 'NO_SHOW')
    ),
    CONSTRAINT chk_turno_fechas CHECK (fecha_hora_fin > fecha_hora_inicio)
);

CREATE INDEX idx_turno_cliente
    ON turnos(cliente_id);

CREATE INDEX idx_turno_profesional_fecha
    ON turnos(profesional_id, fecha_hora_inicio);

CREATE INDEX idx_turno_fecha
    ON turnos(fecha_hora_inicio);

CREATE INDEX idx_turno_recordatorio
    ON turnos(fecha_hora_inicio, estado)
    WHERE recordatorio_24h_enviado = FALSE;

ALTER TABLE turnos
    ADD CONSTRAINT ex_turno_profesional_solapado
    EXCLUDE USING gist (
        profesional_id WITH =,
        tsrange(fecha_hora_inicio, fecha_hora_fin, '[)') WITH &&
    )
    WHERE (estado <> 'CANCELADO');
