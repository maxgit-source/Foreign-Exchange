CREATE TABLE IF NOT EXISTS categorias (
    id          BIGSERIAL       PRIMARY KEY,
    nombre      VARCHAR(100)    NOT NULL,
    descripcion TEXT,
    orden       INTEGER         NOT NULL DEFAULT 0,
    activo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_categoria_nombre
    ON categorias(nombre);

CREATE INDEX IF NOT EXISTS idx_categoria_activo_orden
    ON categorias(activo, orden, nombre);

ALTER TABLE servicios
    ADD COLUMN IF NOT EXISTS categoria_id BIGINT REFERENCES categorias(id),
    ADD COLUMN IF NOT EXISTS imagen_url TEXT;

CREATE INDEX IF NOT EXISTS idx_servicio_categoria
    ON servicios(categoria_id);
