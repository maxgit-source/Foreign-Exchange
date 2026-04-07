CREATE TABLE disponibilidad_profesional (
    id              BIGSERIAL       PRIMARY KEY,
    profesional_id  BIGINT          NOT NULL REFERENCES public.usuarios(id),
    dia             VARCHAR(15)     NOT NULL,
    hora_inicio     TIME            NOT NULL,
    hora_fin        TIME            NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_disponibilidad_dia CHECK (
        dia IN ('LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO')
    ),
    CONSTRAINT chk_disponibilidad_hora CHECK (hora_fin > hora_inicio)
);

CREATE INDEX idx_disponibilidad_profesional_dia
    ON disponibilidad_profesional(profesional_id, dia, activo);
