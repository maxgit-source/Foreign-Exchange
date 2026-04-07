CREATE TABLE staff_invitaciones (
    id          BIGSERIAL     PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email       VARCHAR(255)  NOT NULL,
    token_hash  VARCHAR(64)   NOT NULL,
    expires_at  TIMESTAMP     NOT NULL,
    usado       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_staff_invitacion_email_tenant UNIQUE (email, tenant_id)
);

CREATE UNIQUE INDEX idx_staff_invitacion_token_hash
    ON staff_invitaciones(token_hash);

CREATE INDEX idx_staff_invitacion_tenant
    ON staff_invitaciones(tenant_id, usado, expires_at);
