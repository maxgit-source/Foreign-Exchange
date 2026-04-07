CREATE TABLE IF NOT EXISTS plan_limites (
    plan                 VARCHAR(20)    PRIMARY KEY,
    max_profesionales    INTEGER        NOT NULL DEFAULT 1,
    max_servicios        INTEGER        NOT NULL DEFAULT 5,
    max_turnos_mes       INTEGER        NOT NULL DEFAULT 50,
    max_clientes         INTEGER        NOT NULL DEFAULT 100,
    max_productos        INTEGER        NOT NULL DEFAULT 0,
    tiene_ecommerce      BOOLEAN        NOT NULL DEFAULT FALSE,
    tiene_reportes       BOOLEAN        NOT NULL DEFAULT FALSE,
    tiene_api_publica    BOOLEAN        NOT NULL DEFAULT TRUE,
    tiene_whatsapp       BOOLEAN        NOT NULL DEFAULT FALSE,
    precio_mensual       NUMERIC(10,2),

    CONSTRAINT chk_plan_limites_plan CHECK (plan IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE'))
);

INSERT INTO plan_limites (
    plan, max_profesionales, max_servicios, max_turnos_mes, max_clientes, max_productos,
    tiene_ecommerce, tiene_reportes, tiene_api_publica, tiene_whatsapp, precio_mensual
) VALUES
    ('FREE', 1, 5, 50, 100, 0, FALSE, FALSE, TRUE, FALSE, 0),
    ('BASIC', 3, 20, 200, 500, 50, TRUE, FALSE, TRUE, FALSE, 9.99),
    ('PRO', 10, 50, 1000, 5000, 500, TRUE, TRUE, TRUE, TRUE, 29.99),
    ('ENTERPRISE', -1, -1, -1, -1, -1, TRUE, TRUE, TRUE, TRUE, NULL)
ON CONFLICT (plan) DO NOTHING;
