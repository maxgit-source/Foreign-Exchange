CREATE TABLE IF NOT EXISTS suscripciones (
    id                            BIGSERIAL       PRIMARY KEY,
    tenant_id                     BIGINT          NOT NULL REFERENCES tenants(id) UNIQUE,
    plan                          VARCHAR(20)     NOT NULL,
    estado                        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVA',
    fecha_inicio                  DATE            NOT NULL,
    fecha_vencimiento             DATE,
    stripe_subscription_id        VARCHAR(200),
    mercadopago_subscription_id   VARCHAR(200),
    created_at                    TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_suscripcion_plan CHECK (plan IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE')),
    CONSTRAINT chk_suscripcion_estado CHECK (estado IN ('ACTIVA', 'CANCELADA', 'VENCIDA', 'TRIAL'))
);

CREATE TABLE IF NOT EXISTS historial_planes (
    id             BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL REFERENCES tenants(id),
    plan_anterior   VARCHAR(20),
    plan_nuevo      VARCHAR(20)     NOT NULL,
    motivo          VARCHAR(100),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_historial_planes_plan_anterior CHECK (
        plan_anterior IS NULL OR plan_anterior IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE')
    ),
    CONSTRAINT chk_historial_planes_plan_nuevo CHECK (
        plan_nuevo IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE')
    )
);

CREATE INDEX IF NOT EXISTS idx_historial_planes_tenant_created
    ON historial_planes(tenant_id, created_at DESC);
