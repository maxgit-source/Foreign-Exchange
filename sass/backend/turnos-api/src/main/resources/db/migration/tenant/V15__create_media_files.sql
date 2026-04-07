-- Biblioteca de medios del tenant (imágenes/videos subidos a Cloudinary)
CREATE TABLE IF NOT EXISTS media_files (
    id              BIGSERIAL PRIMARY KEY,
    cloudinary_id   VARCHAR(500) NOT NULL,            -- public_id de Cloudinary
    url             TEXT NOT NULL,
    nombre          VARCHAR(500) NOT NULL,
    tipo            VARCHAR(100) NOT NULL DEFAULT 'image', -- 'image','video','raw'
    formato         VARCHAR(50),                        -- 'jpg','png','mp4', etc.
    tamanio_bytes   BIGINT,
    ancho           INT,
    alto            INT,
    carpeta         VARCHAR(255) DEFAULT 'general',     -- organización en carpetas
    tags            TEXT,                               -- comma-separated
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_media_tipo     ON media_files(tipo);
CREATE INDEX IF NOT EXISTS idx_media_carpeta  ON media_files(carpeta);
CREATE INDEX IF NOT EXISTS idx_media_created  ON media_files(created_at DESC);
