CREATE TABLE IF NOT EXISTS categorias_producto (
    id           BIGSERIAL       PRIMARY KEY,
    nombre       VARCHAR(100)    NOT NULL,
    descripcion  TEXT,
    imagen_url   TEXT,
    activo       BOOLEAN         NOT NULL DEFAULT TRUE,
    orden        INTEGER         NOT NULL DEFAULT 0,
    created_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_categoria_producto_activo_orden
    ON categorias_producto(activo, orden, nombre);

CREATE TABLE IF NOT EXISTS productos (
    id              BIGSERIAL        PRIMARY KEY,
    categoria_id    BIGINT           REFERENCES categorias_producto(id),
    nombre          VARCHAR(200)     NOT NULL,
    descripcion     TEXT,
    precio          NUMERIC(10,2)    NOT NULL,
    precio_oferta   NUMERIC(10,2),
    stock           INTEGER          NOT NULL DEFAULT 0,
    sku             VARCHAR(100),
    imagenes        TEXT[]           NOT NULL DEFAULT ARRAY[]::TEXT[],
    activo          BOOLEAN          NOT NULL DEFAULT TRUE,
    tipo            VARCHAR(20)      NOT NULL DEFAULT 'FISICO',
    peso_kg         NUMERIC(8,3),
    created_at      TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP        NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_producto_sku UNIQUE (sku),
    CONSTRAINT chk_producto_precio CHECK (precio >= 0),
    CONSTRAINT chk_producto_precio_oferta CHECK (precio_oferta IS NULL OR precio_oferta >= 0),
    CONSTRAINT chk_producto_precio_oferta_menor CHECK (precio_oferta IS NULL OR precio_oferta <= precio),
    CONSTRAINT chk_producto_stock CHECK (stock >= -1),
    CONSTRAINT chk_producto_tipo CHECK (tipo IN ('FISICO', 'DIGITAL', 'SERVICIO')),
    CONSTRAINT chk_producto_peso CHECK (peso_kg IS NULL OR peso_kg >= 0)
);

CREATE INDEX IF NOT EXISTS idx_productos_activo
    ON productos(activo, nombre);

CREATE INDEX IF NOT EXISTS idx_productos_categoria
    ON productos(categoria_id);

CREATE INDEX IF NOT EXISTS idx_productos_precio
    ON productos(precio);
