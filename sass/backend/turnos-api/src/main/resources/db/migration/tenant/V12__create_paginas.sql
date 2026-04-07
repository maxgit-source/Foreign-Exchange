-- Páginas del sitio del tenant (home, about, contact, custom...)
CREATE TABLE IF NOT EXISTS paginas (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(200) NOT NULL UNIQUE,
    titulo          VARCHAR(255) NOT NULL,
    descripcion     TEXT,
    activa          BOOLEAN NOT NULL DEFAULT TRUE,
    es_home         BOOLEAN NOT NULL DEFAULT FALSE,    -- solo una puede ser home
    en_nav          BOOLEAN NOT NULL DEFAULT TRUE,     -- aparece en menú de navegación
    orden_nav       INT NOT NULL DEFAULT 0,
    -- SEO de página
    seo_titulo      VARCHAR(255),
    seo_descripcion TEXT,
    og_imagen_url   TEXT,
    -- Timestamps
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Índice para búsqueda por slug
CREATE INDEX IF NOT EXISTS idx_paginas_slug ON paginas(slug);

-- Página home inicial
INSERT INTO paginas (slug, titulo, activa, es_home, en_nav, orden_nav) VALUES ('home', 'Inicio', TRUE, TRUE, FALSE, 0);
INSERT INTO paginas (slug, titulo, activa, es_home, en_nav, orden_nav) VALUES ('nosotros', 'Nosotros', TRUE, FALSE, TRUE, 1);
INSERT INTO paginas (slug, titulo, activa, es_home, en_nav, orden_nav) VALUES ('contacto', 'Contacto', TRUE, FALSE, TRUE, 2);
