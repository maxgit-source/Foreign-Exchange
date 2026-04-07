CREATE TABLE audit_log (
    id          BIGSERIAL     PRIMARY KEY,
    tenant_id   BIGINT,
    usuario_id  BIGINT,
    accion      VARCHAR(30)   NOT NULL,
    entidad     VARCHAR(120)  NOT NULL,
    entidad_id  BIGINT,
    detalle     TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_created
    ON audit_log(tenant_id, created_at DESC);

CREATE INDEX idx_audit_usuario_created
    ON audit_log(usuario_id, created_at DESC);
