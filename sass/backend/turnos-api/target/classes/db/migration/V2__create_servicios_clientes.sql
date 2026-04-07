-- ============================================================
-- V2 — Servicios y Clientes
-- Catálogo de servicios y base de clientes del negocio.
-- ============================================================

CREATE TABLE servicios (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL    REFERENCES tenants(id),
    nombre              VARCHAR(200)    NOT NULL,
    descripcion         TEXT,
    duracion_minutos    INTEGER         NOT NULL,
    precio              NUMERIC(10,2)   NOT NULL,
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_servicio_duracion CHECK (duracion_minutos >= 5 AND duracion_minutos <= 480),
    CONSTRAINT chk_servicio_precio   CHECK (precio > 0)
);

COMMENT ON TABLE  servicios                  IS 'Catálogo de servicios ofrecidos por el negocio';
COMMENT ON COLUMN servicios.duracion_minutos IS 'Duración en minutos. Determina la ventana de tiempo bloqueada en la agenda';

CREATE INDEX idx_servicio_tenant ON servicios(tenant_id, activo);

-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE clientes (
    id          BIGSERIAL       PRIMARY KEY,
    tenant_id   BIGINT          NOT NULL    REFERENCES tenants(id),
    nombre      VARCHAR(100)    NOT NULL,
    apellido    VARCHAR(100),
    email       VARCHAR(255),
    telefono    VARCHAR(30),
    notas       TEXT,
    activo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- Un cliente (email) puede existir en múltiples tenants sin conflicto
    CONSTRAINT uk_cliente_email_tenant UNIQUE (email, tenant_id)
);

COMMENT ON TABLE clientes IS 'Clientes de cada negocio. Aislados por tenant.';
COMMENT ON CONSTRAINT uk_cliente_email_tenant ON clientes
    IS 'El mismo email puede pertenecer a distintos tenants (la persona puede ser cliente de múltiples negocios)';

CREATE INDEX idx_cliente_tenant ON clientes(tenant_id, activo);
CREATE INDEX idx_cliente_email  ON clientes(email, tenant_id);
