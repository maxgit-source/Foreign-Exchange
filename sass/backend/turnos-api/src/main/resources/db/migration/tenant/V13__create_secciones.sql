-- Secciones/bloques que componen cada página (Page Builder)
-- Tipos válidos: HERO, TEXTO, IMAGEN, GALERIA, GRID_SERVICIOS, GRID_PRODUCTOS,
-- TESTIMONIOS, CTA, VIDEO, CONTACTO, MAPA, SEPARADOR, CONTADOR, EQUIPO, FAQ,
-- BLOG_RECIENTE, RESERVA, HTML_CUSTOM
CREATE TABLE IF NOT EXISTS secciones (
    id                  BIGSERIAL PRIMARY KEY,
    pagina_id           BIGINT NOT NULL REFERENCES paginas(id) ON DELETE CASCADE,
    tipo                VARCHAR(50) NOT NULL,
    orden               INT NOT NULL DEFAULT 0,
    visible             BOOLEAN NOT NULL DEFAULT TRUE,
    -- Config del bloque (JSON): título, subtítulo, texto, urls, botones, config específica del tipo
    config              TEXT NOT NULL DEFAULT '{}',
    -- Animación de entrada
    animacion_entrada   VARCHAR(100),   -- 'fade-in', 'slide-up', 'zoom-in', 'bounce', NULL=sin anim
    animacion_duracion  INT DEFAULT 600, -- ms
    animacion_delay     INT DEFAULT 0,   -- ms de delay
    -- Estilos custom del bloque (JSON): bg_color, text_color, padding, etc.
    estilos             TEXT DEFAULT '{}',
    -- Timestamps
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_secciones_pagina ON secciones(pagina_id, orden);

-- Sección hero inicial en la home
INSERT INTO secciones (pagina_id, tipo, orden, config) VALUES (
    (SELECT id FROM paginas WHERE slug = 'home' LIMIT 1),
    'HERO',
    0,
    '{"titulo":"Bienvenido a nuestro negocio","subtitulo":"Reserva tu turno online de manera fácil y rápida","cta_texto":"Reservar ahora","cta_url":"/reservar","imagen_url":null}'
);
