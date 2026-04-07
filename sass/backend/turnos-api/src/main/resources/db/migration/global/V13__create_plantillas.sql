-- Catálogo global de plantillas disponibles en la plataforma
CREATE TABLE IF NOT EXISTS plantillas (
    id              BIGSERIAL PRIMARY KEY,
    codigo          VARCHAR(100) NOT NULL UNIQUE,
    nombre          VARCHAR(200) NOT NULL,
    descripcion     TEXT,
    categoria       VARCHAR(100) NOT NULL,          -- 'ecommerce','turnos','blog','restaurante','gym', etc.
    preview_url     TEXT,                            -- URL imagen preview
    thumbnail_url   TEXT,                            -- URL miniatura
    tags            TEXT,                            -- comma-separated: 'moderno,oscuro,animado'
    activa          BOOLEAN NOT NULL DEFAULT TRUE,
    orden           INT NOT NULL DEFAULT 0,
    config_default  TEXT,                            -- JSON con colores/fuentes por defecto de la plantilla
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Plantillas iniciales
INSERT INTO plantillas (codigo, nombre, descripcion, categoria, tags, orden) VALUES
('comida',          'Comida & Delivery',    'Perfecta para restaurantes, cafés y delivery',          'ecommerce', 'moderno,comida,oscuro',      1),
('electronica',     'Tech & Electrónica',   'Diseño limpio para tiendas de tecnología',              'ecommerce', 'minimalista,tech,blanco',    2),
('ropa',            'Fashion Store',        'Elegante para boutiques y tiendas de ropa',             'ecommerce', 'elegante,moda,claro',        3),
('venta_deportiva', 'Sport & Fitness',      'Energética para artículos deportivos y gym',            'ecommerce', 'dinamico,deporte,vibrante',  4),
('turnos_basico',   'Turnos Simple',        'Página limpia enfocada en reservas de turnos',          'turnos',    'simple,claro,profesional',   5),
('salud',           'Salud & Bienestar',    'Para clínicas, spas y centros de bienestar',            'turnos',    'sereno,salud,claro',         6),
('educacion',       'Academia & Cursos',    'Para institutos educativos y clases particulares',      'turnos',    'educacion,profesional,azul', 7),
('blog_minimal',    'Blog Minimalista',     'Diseño editorial limpio para blogs de contenido',       'blog',      'blog,minimalista,tipografia',8);
