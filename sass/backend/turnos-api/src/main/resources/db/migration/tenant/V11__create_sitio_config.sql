-- Configuración del sitio público del tenant (1 fila por tenant)
CREATE TABLE IF NOT EXISTS sitio_config (
    id                  BIGSERIAL PRIMARY KEY,
    plantilla_codigo    VARCHAR(100) NOT NULL DEFAULT 'turnos_basico',
    dominio_custom      VARCHAR(255),                -- ej: "mitienda.com" (sin https)
    nombre_sitio        VARCHAR(255),
    descripcion_sitio   TEXT,
    favicon_url         TEXT,
    logo_url            TEXT,
    -- Paleta extendida (JSON): {"primario":"#FF5722","secundario":"#FFC107","fondo":"#fff","texto":"#111","acento":"#E91E63"}
    paleta              TEXT,
    -- Redes sociales (JSON): {"instagram":"url","facebook":"url","twitter":"url","whatsapp":"+54..."}
    redes_sociales      TEXT,
    -- SEO global
    seo_titulo          VARCHAR(255),
    seo_descripcion     TEXT,
    seo_keywords        TEXT,
    og_imagen_url       TEXT,
    -- Opciones visuales
    fuente_principal    VARCHAR(100) DEFAULT 'Inter',
    fuente_titulos      VARCHAR(100) DEFAULT 'Inter',
    animaciones_activas BOOLEAN NOT NULL DEFAULT TRUE,
    modo_oscuro_default BOOLEAN NOT NULL DEFAULT FALSE,
    -- Configuración de módulos habilitados
    modulo_tienda       BOOLEAN NOT NULL DEFAULT TRUE,
    modulo_blog         BOOLEAN NOT NULL DEFAULT FALSE,
    modulo_turnos       BOOLEAN NOT NULL DEFAULT TRUE,
    -- Mantenimiento
    sitio_activo        BOOLEAN NOT NULL DEFAULT TRUE,
    mensaje_mantenimiento TEXT,
    -- Config extra (JSON libre para extensiones futuras)
    config_extra        TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Inserta fila inicial cuando se aprovisiona el tenant (puede hacerse también desde código)
INSERT INTO sitio_config (plantilla_codigo, nombre_sitio, sitio_activo) VALUES ('turnos_basico', 'Mi Negocio', TRUE);
