CREATE TABLE IF NOT EXISTS pedidos (
    id                 BIGSERIAL        PRIMARY KEY,
    cliente_id         BIGINT           REFERENCES clientes(id),
    nombre_cliente     VARCHAR(200),
    email_cliente      VARCHAR(200),
    telefono_cliente   VARCHAR(30),
    estado             VARCHAR(30)      NOT NULL DEFAULT 'PENDIENTE_PAGO',
    subtotal           NUMERIC(10,2)    NOT NULL,
    descuento          NUMERIC(10,2)    NOT NULL DEFAULT 0,
    costo_envio        NUMERIC(10,2)    NOT NULL DEFAULT 0,
    total              NUMERIC(10,2)    NOT NULL,
    direccion_envio    TEXT,
    notas              TEXT,
    payment_intent_id  VARCHAR(200),
    payment_method     VARCHAR(50),
    paid_at            TIMESTAMPTZ,
    created_at         TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP        NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pedido_estado CHECK (
        estado IN ('PENDIENTE_PAGO', 'PAGO_CONFIRMADO', 'EN_PREPARACION', 'ENVIADO', 'ENTREGADO', 'CANCELADO')
    ),
    CONSTRAINT chk_pedido_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_pedido_descuento CHECK (descuento >= 0),
    CONSTRAINT chk_pedido_costo_envio CHECK (costo_envio >= 0),
    CONSTRAINT chk_pedido_total CHECK (total >= 0),
    CONSTRAINT chk_pedido_payment_method CHECK (
        payment_method IS NULL OR payment_method IN ('STRIPE', 'MERCADOPAGO', 'EFECTIVO', 'TRANSFERENCIA')
    )
);

CREATE INDEX IF NOT EXISTS idx_pedidos_created
    ON pedidos(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pedidos_cliente
    ON pedidos(cliente_id);

CREATE INDEX IF NOT EXISTS idx_pedidos_estado
    ON pedidos(estado);

CREATE INDEX IF NOT EXISTS idx_pedidos_payment_intent
    ON pedidos(payment_intent_id);

CREATE TABLE IF NOT EXISTS pedido_items (
    id               BIGSERIAL        PRIMARY KEY,
    pedido_id        BIGINT           NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    producto_id      BIGINT           REFERENCES productos(id),
    turno_id         BIGINT           REFERENCES turnos(id),
    nombre           VARCHAR(200)     NOT NULL,
    precio_unitario  NUMERIC(10,2)    NOT NULL,
    cantidad         INTEGER          NOT NULL DEFAULT 1,
    subtotal         NUMERIC(10,2)    NOT NULL,

    CONSTRAINT chk_pedido_item_origen CHECK (
        ((producto_id IS NOT NULL)::int + (turno_id IS NOT NULL)::int) = 1
    ),
    CONSTRAINT chk_pedido_item_precio CHECK (precio_unitario >= 0),
    CONSTRAINT chk_pedido_item_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_pedido_item_subtotal CHECK (subtotal >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pedido_items_pedido
    ON pedido_items(pedido_id);

CREATE INDEX IF NOT EXISTS idx_pedido_items_producto
    ON pedido_items(producto_id);

CREATE INDEX IF NOT EXISTS idx_pedido_items_turno
    ON pedido_items(turno_id);
