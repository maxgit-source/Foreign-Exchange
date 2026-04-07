-- ============================================================
-- V1 — Tenants y Usuarios
-- Base del sistema multi-tenant.
-- ============================================================

CREATE TABLE tenants (
    id              BIGSERIAL       PRIMARY KEY,
    nombre          VARCHAR(200)    NOT NULL,
    slug            VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    telefono        VARCHAR(30),
    direccion       VARCHAR(500),
    plan            VARCHAR(20)     NOT NULL DEFAULT 'FREE',
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    timezone        VARCHAR(60)     NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    logo_url        VARCHAR(500),
    color_primario  VARCHAR(7)               DEFAULT '#6366f1',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tenant_slug  UNIQUE (slug),
    CONSTRAINT uk_tenant_email UNIQUE (email),
    CONSTRAINT chk_tenant_plan CHECK (plan IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE'))
);

COMMENT ON TABLE  tenants              IS 'Negocios suscriptores de la plataforma';
COMMENT ON COLUMN tenants.slug         IS 'Identificador URL-friendly del negocio (ej: mi-salon)';
COMMENT ON COLUMN tenants.plan         IS 'Plan de suscripción actual';
COMMENT ON COLUMN tenants.color_primario IS 'Color de marca en formato HEX para branding del widget';

CREATE TABLE usuarios (
    id          BIGSERIAL       PRIMARY KEY,
    nombre      VARCHAR(200)    NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    role        VARCHAR(20)     NOT NULL,
    tenant_id   BIGINT          NOT NULL    REFERENCES tenants(id),
    enabled     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT chk_usuario_role CHECK (role IN ('ADMIN', 'OWNER', 'STAFF', 'CLIENT'))
);

COMMENT ON TABLE  usuarios         IS 'Usuarios de la plataforma (staff y propietarios)';
COMMENT ON COLUMN usuarios.enabled IS 'Si false, el usuario no puede autenticarse';

CREATE INDEX idx_usuario_email     ON usuarios(email);
CREATE INDEX idx_usuario_tenant    ON usuarios(tenant_id);
