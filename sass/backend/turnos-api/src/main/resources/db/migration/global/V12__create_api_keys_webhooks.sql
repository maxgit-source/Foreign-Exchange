CREATE TABLE api_keys (
    id          BIGSERIAL       PRIMARY KEY,
    tenant_id   BIGINT          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    nombre      VARCHAR(100)    NOT NULL,
    key_hash    VARCHAR(64)     NOT NULL UNIQUE,
    permisos    TEXT,
    activo      BOOLEAN         NOT NULL DEFAULT TRUE,
    ultimo_uso  TIMESTAMP,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_api_key_hash UNIQUE (key_hash)
);

CREATE INDEX idx_api_keys_tenant   ON api_keys(tenant_id, activo);
CREATE INDEX idx_api_keys_key_hash ON api_keys(key_hash);

CREATE TABLE webhooks (
    id          BIGSERIAL       PRIMARY KEY,
    tenant_id   BIGINT          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    url         TEXT            NOT NULL,
    eventos     TEXT            NOT NULL,
    secret_hash VARCHAR(64),
    activo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhooks_tenant ON webhooks(tenant_id, activo);
