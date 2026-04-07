CREATE TABLE servicios (
    id                  BIGSERIAL       PRIMARY KEY,
    nombre              VARCHAR(200)    NOT NULL,
    descripcion         TEXT,
    duracion_minutos    INTEGER         NOT NULL,
    precio              NUMERIC(10,2)   NOT NULL,
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_servicio_duracion CHECK (duracion_minutos BETWEEN 5 AND 480),
    CONSTRAINT chk_servicio_precio CHECK (precio > 0)
);

CREATE INDEX idx_servicio_activo
    ON servicios(activo);

CREATE TABLE clientes (
    id          BIGSERIAL       PRIMARY KEY,
    nombre      VARCHAR(100)    NOT NULL,
    apellido    VARCHAR(100),
    email       VARCHAR(255),
    telefono    VARCHAR(30),
    notas       TEXT,
    activo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_cliente_email UNIQUE (email)
);

CREATE INDEX idx_cliente_activo
    ON clientes(activo);

CREATE INDEX idx_cliente_email
    ON clientes(email);
