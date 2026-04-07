-- Blog del tenant
CREATE TABLE IF NOT EXISTS blog_categorias (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    slug        VARCHAR(150) NOT NULL UNIQUE,
    descripcion TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_blog_cat_slug ON blog_categorias(slug);

-- Estado: BORRADOR, PUBLICADO, ARCHIVADO
CREATE TABLE IF NOT EXISTS blog_posts (
    id              BIGSERIAL PRIMARY KEY,
    titulo          VARCHAR(500) NOT NULL,
    slug            VARCHAR(500) NOT NULL UNIQUE,
    extracto        TEXT,
    contenido       TEXT NOT NULL,                   -- rich text / markdown / HTML
    imagen_portada  TEXT,                            -- URL Cloudinary
    estado          VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicado_at    TIMESTAMP,                       -- NULL si es borrador
    categoria_id    BIGINT REFERENCES blog_categorias(id) ON DELETE SET NULL,
    autor_nombre    VARCHAR(255),                    -- nombre del autor (desnormalizado)
    -- SEO
    seo_titulo      VARCHAR(255),
    seo_descripcion TEXT,
    og_imagen_url   TEXT,
    tags            TEXT,                            -- comma-separated
    -- Métricas básicas
    vistas          BIGINT NOT NULL DEFAULT 0,
    -- Timestamps
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_blog_posts_slug   ON blog_posts(slug);
CREATE INDEX IF NOT EXISTS idx_blog_posts_estado ON blog_posts(estado);
CREATE INDEX IF NOT EXISTS idx_blog_posts_pub_at ON blog_posts(publicado_at DESC);
