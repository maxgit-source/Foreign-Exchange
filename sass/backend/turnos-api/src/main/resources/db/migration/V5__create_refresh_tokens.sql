-- ============================================================
-- V5 — Refresh Tokens
-- Tokens de renovación de sesión (hasheados, nunca en claro).
-- ============================================================

CREATE TABLE refresh_tokens (
    id          BIGSERIAL       PRIMARY KEY,
    usuario_id  BIGINT          NOT NULL    REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)     NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  refresh_tokens            IS 'Refresh tokens de autenticación. Solo se almacena el hash SHA-256.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'Hash SHA-256 del token real. El token en claro nunca se persiste.';

CREATE INDEX idx_refresh_token_hash    ON refresh_tokens(token_hash) WHERE revoked = FALSE;
CREATE INDEX idx_refresh_token_usuario ON refresh_tokens(usuario_id);
