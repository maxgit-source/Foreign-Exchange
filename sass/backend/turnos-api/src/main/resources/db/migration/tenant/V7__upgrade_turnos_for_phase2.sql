ALTER TABLE turnos
    ADD COLUMN IF NOT EXISTS turno_padre_id BIGINT REFERENCES turnos(id),
    ADD COLUMN IF NOT EXISTS es_recurrente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS recurrencia_semanas INTEGER,
    ADD COLUMN IF NOT EXISTS fecha_hora_inicio_local TIMESTAMP,
    ADD COLUMN IF NOT EXISTS fecha_hora_fin_local TIMESTAMP;

ALTER TABLE turnos
    DROP CONSTRAINT IF EXISTS chk_turno_recurrencia;

ALTER TABLE turnos
    ADD CONSTRAINT chk_turno_recurrencia CHECK (
        recurrencia_semanas IS NULL OR recurrencia_semanas BETWEEN 1 AND 52
    );

CREATE INDEX IF NOT EXISTS idx_turno_padre
    ON turnos(turno_padre_id);

CREATE TABLE IF NOT EXISTS turno_servicios (
    turno_id     BIGINT NOT NULL REFERENCES turnos(id) ON DELETE CASCADE,
    servicio_id  BIGINT NOT NULL REFERENCES servicios(id),
    PRIMARY KEY (turno_id, servicio_id)
);

CREATE INDEX IF NOT EXISTS idx_turno_servicios_servicio
    ON turno_servicios(servicio_id);

INSERT INTO turno_servicios (turno_id, servicio_id)
SELECT t.id, t.servicio_id
FROM turnos t
WHERE t.servicio_id IS NOT NULL
ON CONFLICT DO NOTHING;

UPDATE turnos
SET
    fecha_hora_inicio_local = COALESCE(fecha_hora_inicio_local, fecha_hora_inicio::timestamp),
    fecha_hora_fin_local = COALESCE(fecha_hora_fin_local, fecha_hora_fin::timestamp)
WHERE fecha_hora_inicio IS NOT NULL
  AND fecha_hora_fin IS NOT NULL;

ALTER TABLE turnos
    ALTER COLUMN servicio_id DROP NOT NULL;

ALTER TABLE turnos
    DROP CONSTRAINT IF EXISTS ex_turno_profesional_solapado;

DO $$
DECLARE
    tenant_timezone TEXT;
BEGIN
    SELECT COALESCE(t.timezone, 'America/Argentina/Buenos_Aires')
      INTO tenant_timezone
      FROM public.tenants t
     WHERE ('tenant_' || regexp_replace(replace(lower(t.slug), '-', '_'), '[^a-z0-9_]', '', 'g')) = current_schema()
     LIMIT 1;

    tenant_timezone := COALESCE(tenant_timezone, 'America/Argentina/Buenos_Aires');

    EXECUTE format(
        'ALTER TABLE turnos ALTER COLUMN fecha_hora_inicio TYPE TIMESTAMPTZ USING (fecha_hora_inicio_local AT TIME ZONE %L)',
        tenant_timezone
    );

    EXECUTE format(
        'ALTER TABLE turnos ALTER COLUMN fecha_hora_fin TYPE TIMESTAMPTZ USING (fecha_hora_fin_local AT TIME ZONE %L)',
        tenant_timezone
    );
END $$;

DROP INDEX IF EXISTS idx_turno_recordatorio;

CREATE INDEX IF NOT EXISTS idx_turno_recordatorio
    ON turnos(fecha_hora_inicio, estado)
    WHERE recordatorio_24h_enviado = FALSE;

ALTER TABLE turnos
    ADD CONSTRAINT ex_turno_profesional_solapado
    EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(fecha_hora_inicio, fecha_hora_fin, '[)') WITH &&
    )
    WHERE (estado <> 'CANCELADO');
