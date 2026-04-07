# FASE 3 — E-commerce y Pagos
**Objetivo:** Transformar la plataforma en un SaaS que también vende productos y cobra por turnos.
**Cuando ejecutar:** Después de FASE 2. Esta fase es la más ambiciosa.

---

## 3.1 — Catálogo de Productos

### Migración V11__create_productos.sql
```sql
CREATE TABLE categorias_producto (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    imagen_url TEXT,
    activo BOOLEAN DEFAULT TRUE,
    orden INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE productos (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    categoria_id BIGINT REFERENCES categorias_producto(id),
    nombre VARCHAR(200) NOT NULL,
    descripcion TEXT,
    precio NUMERIC(10,2) NOT NULL CHECK (precio >= 0),
    precio_oferta NUMERIC(10,2),                    -- NULL = sin oferta
    stock INTEGER NOT NULL DEFAULT 0,               -- -1 = ilimitado (servicio digital)
    sku VARCHAR(100),
    imagenes TEXT[],                                -- array de URLs
    activo BOOLEAN DEFAULT TRUE,
    tipo VARCHAR(20) DEFAULT 'FISICO',              -- FISICO, DIGITAL, SERVICIO
    peso_kg NUMERIC(8,3),                           -- para cálculo de envío
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(sku, tenant_id)
);

CREATE INDEX idx_productos_tenant ON productos(tenant_id, activo);
CREATE INDEX idx_productos_categoria ON productos(categoria_id);
```

### Endpoints
```
GET    /api/productos                    → lista paginada con filtros (nombre, categoría, precio)
GET    /api/productos/{id}               → detalle
POST   /api/productos                    → crear [OWNER]
PUT    /api/productos/{id}               → actualizar [OWNER]
DELETE /api/productos/{id}               → desactivar [OWNER]
PATCH  /api/productos/{id}/stock         → actualizar stock [OWNER, STAFF]
POST   /api/productos/{id}/imagenes      → subir imagen [OWNER]

GET    /public/{slug}/productos          → catálogo público
GET    /public/{slug}/productos/{id}     → detalle público
```

### ProductoRequestDTO
```java
public record ProductoRequestDTO(
    @NotBlank @Size(max=200) String nombre,
    String descripcion,
    Long categoriaId,
    @NotNull @DecimalMin("0") BigDecimal precio,
    BigDecimal precioOferta,
    @NotNull @Min(-1) Integer stock,
    String sku,
    TipoProducto tipo,
    BigDecimal pesoKg
) {}
```

---

## 3.2 — Carrito y Pedidos

### Migración V12__create_pedidos.sql
```sql
CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    cliente_id BIGINT REFERENCES clientes(id),
    -- Para pedidos de clientes no registrados:
    nombre_cliente VARCHAR(200),
    email_cliente VARCHAR(200),
    telefono_cliente VARCHAR(30),
    
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_PAGO',
    -- PENDIENTE_PAGO → PAGO_CONFIRMADO → EN_PREPARACION → ENVIADO → ENTREGADO → CANCELADO
    
    subtotal NUMERIC(10,2) NOT NULL,
    descuento NUMERIC(10,2) DEFAULT 0,
    costo_envio NUMERIC(10,2) DEFAULT 0,
    total NUMERIC(10,2) NOT NULL,
    
    direccion_envio TEXT,
    notas TEXT,
    
    payment_intent_id VARCHAR(200),    -- Stripe / Mercado Pago ID
    payment_method VARCHAR(50),        -- STRIPE, MERCADOPAGO, EFECTIVO, TRANSFERENCIA
    paid_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE pedido_items (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id),
    producto_id BIGINT REFERENCES productos(id),
    turno_id BIGINT REFERENCES turnos(id),    -- si el ítem es un turno/servicio
    nombre VARCHAR(200) NOT NULL,             -- snapshot del nombre al momento de compra
    precio_unitario NUMERIC(10,2) NOT NULL,   -- snapshot del precio
    cantidad INTEGER NOT NULL DEFAULT 1,
    subtotal NUMERIC(10,2) NOT NULL
);

CREATE INDEX idx_pedidos_tenant ON pedidos(tenant_id, created_at DESC);
CREATE INDEX idx_pedidos_cliente ON pedidos(cliente_id);
CREATE INDEX idx_pedidos_estado ON pedidos(estado);
```

### Endpoints
```
POST   /api/pedidos                  → crear pedido
GET    /api/pedidos                  → listar pedidos del tenant (paginado, filtrable)
GET    /api/pedidos/{id}             → detalle de pedido
PATCH  /api/pedidos/{id}/estado      → cambiar estado [OWNER, STAFF]
POST   /public/{slug}/checkout       → checkout público (crea pedido + inicia pago)
```

---

## 3.3 — Integración de Pagos

### Opción A: Mercado Pago (recomendado para LATAM)

**Dependencia:**
```xml
<dependency>
    <groupId>com.mercadopago</groupId>
    <artifactId>sdk-java</artifactId>
    <version>2.1.24</version>
</dependency>
```

**MercadoPagoService.java:**
```java
@Service
public class MercadoPagoService {
    
    @Value("${mercadopago.access-token}")
    private String accessToken;
    
    public String crearPreferencia(Pedido pedido) {
        MercadoPagoConfig.setAccessToken(accessToken);
        
        PreferenceClient client = new PreferenceClient();
        
        List<PreferenceItemRequest> items = pedido.getItems().stream()
            .map(item -> PreferenceItemRequest.builder()
                .title(item.getNombre())
                .quantity(item.getCantidad())
                .unitPrice(item.getPrecioUnitario())
                .build()
            ).toList();
        
        PreferenceRequest preference = PreferenceRequest.builder()
            .items(items)
            .externalReference(pedido.getId().toString())
            .backUrls(PreferenceBackUrlsRequest.builder()
                .success("https://app.tunegocio.com/pago/exito")
                .failure("https://app.tunegocio.com/pago/error")
                .build())
            .notificationUrl("https://api.tunegocio.com/api/webhooks/mercadopago")
            .build();
        
        Preference result = client.create(preference);
        return result.getInitPoint();  // URL de checkout de MP
    }
}
```

### Opción B: Stripe

**Dependencia:**
```xml
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>25.3.0</version>
</dependency>
```

**StripeService.java:**
```java
@Service
public class StripeService {
    
    public String crearPaymentIntent(Pedido pedido) {
        Stripe.apiKey = stripeSecretKey;
        
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount((long)(pedido.getTotal().doubleValue() * 100))  // centavos
            .setCurrency("ars")  // o "usd"
            .putMetadata("pedido_id", pedido.getId().toString())
            .putMetadata("tenant_id", pedido.getTenant().getId().toString())
            .build();
        
        PaymentIntent intent = PaymentIntent.create(params);
        return intent.getClientSecret();  // al frontend para Stripe.js
    }
}
```

### WebhookController.java (recibir confirmaciones de pago)
```java
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    
    @PostMapping("/mercadopago")
    public ResponseEntity<Void> mercadoPago(@RequestBody Map<String, Object> payload,
                                             @RequestHeader("x-signature") String sig) {
        // 1. Validar firma del webhook
        // 2. Extraer pedidoId de external_reference
        // 3. Actualizar pedido.estado = PAGO_CONFIRMADO
        // 4. Descontar stock de productos
        // 5. Enviar email de confirmación
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(@RequestBody String payload,
                                        @RequestHeader("Stripe-Signature") String sig) {
        // Similar con Stripe.constructEvent()
        return ResponseEntity.ok().build();
    }
}
```

---

## 3.4 — Enforcement de Planes

### Migración V13__create_plan_limites.sql
```sql
CREATE TABLE plan_limites (
    plan VARCHAR(20) PRIMARY KEY,
    max_profesionales INTEGER NOT NULL DEFAULT 1,
    max_servicios INTEGER NOT NULL DEFAULT 5,
    max_turnos_mes INTEGER NOT NULL DEFAULT 50,
    max_clientes INTEGER NOT NULL DEFAULT 100,
    max_productos INTEGER NOT NULL DEFAULT 0,    -- 0 = no permite e-commerce
    tiene_ecommerce BOOLEAN DEFAULT FALSE,
    tiene_reportes BOOLEAN DEFAULT FALSE,
    tiene_api_publica BOOLEAN DEFAULT TRUE,
    tiene_whatsapp BOOLEAN DEFAULT FALSE,
    precio_mensual NUMERIC(10,2)
);

INSERT INTO plan_limites VALUES 
    ('FREE',       1,   5,    50,   100,   0, false, false, true,  false, 0),
    ('BASIC',      3,  20,   200,   500,  50, true,  false, true,  false, 9.99),
    ('PRO',       10,  50,  1000,  5000, 500, true,  true,  true,  true,  29.99),
    ('ENTERPRISE', -1, -1,    -1,    -1,  -1, true,  true,  true,  true,  NULL);
-- -1 = ilimitado
```

### PlanValidator.java
```java
@Service
@RequiredArgsConstructor
public class PlanValidator {
    
    private final PlanLimitesRepository planLimitesRepo;
    private final TurnoRepository turnoRepo;
    // etc.
    
    public void validarPuedeCrearTurno(Tenant tenant) {
        PlanLimites limites = planLimitesRepo.findById(tenant.getPlan().name()).orElseThrow();
        if (limites.getMaxTurnosMes() == -1) return; // ilimitado
        
        long turnosMes = turnoRepo.countByTenantAndMesActual(tenant.getId());
        if (turnosMes >= limites.getMaxTurnosMes()) {
            throw new BusinessException(
                "Límite de turnos mensuales alcanzado para el plan " + tenant.getPlan() + 
                ". Upgrade a PRO para más turnos."
            );
        }
    }
    
    public void validarPuedeCrearProfesional(Tenant tenant) { ... }
    public void validarPuedeUsarEcommerce(Tenant tenant) { ... }
    public void validarPuedeVerReportes(Tenant tenant) { ... }
}
```

**Inyectar en cada service:**
```java
// En TurnoService.crear():
planValidator.validarPuedeCrearTurno(usuario.getTenant());
// ... resto de la lógica
```

---

## 3.5 — Gestión de Suscripciones del Tenant

### Migración V14__create_suscripciones.sql
```sql
CREATE TABLE suscripciones (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) UNIQUE,
    plan VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',   -- ACTIVA, CANCELADA, VENCIDA, TRIAL
    fecha_inicio DATE NOT NULL,
    fecha_vencimiento DATE,
    stripe_subscription_id VARCHAR(200),
    mercadopago_subscription_id VARCHAR(200),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE historial_planes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    plan_anterior VARCHAR(20),
    plan_nuevo VARCHAR(20) NOT NULL,
    motivo VARCHAR(100),    -- UPGRADE, DOWNGRADE, VENCIMIENTO, ADMIN
    created_at TIMESTAMP DEFAULT NOW()
);
```

### Endpoints (solo ADMIN de plataforma)
```
GET    /api/admin/tenants                    → listar todos los tenants con plan/estado
PATCH  /api/admin/tenants/{id}/plan          → cambiar plan [ADMIN]
GET    /api/admin/tenants/{id}/suscripcion   → detalle de suscripción
```

---

## Checklist FASE 3

- [ ] V11: categorias_producto + productos
- [ ] ProductoController + ProductoService con todos los endpoints
- [ ] ProductoRequestDTO + ProductoResponseDTO
- [ ] Endpoint de upload de imágenes de productos
- [ ] V12: pedidos + pedido_items
- [ ] PedidoController + PedidoService
- [ ] PedidoRequestDTO + PedidoResponseDTO con items
- [ ] Máquina de estados de pedido (validar transiciones)
- [ ] MercadoPagoService.crearPreferencia()
- [ ] StripeService.crearPaymentIntent()
- [ ] WebhookController (MP + Stripe) con validación de firma
- [ ] Webhook: actualizar estado pedido + descontar stock + enviar email
- [ ] V13: plan_limites + datos iniciales con INSERT
- [ ] PlanLimites entity + PlanLimitesRepository
- [ ] PlanValidator service
- [ ] Inyectar PlanValidator en TurnoService, ClienteService, ProductoService
- [ ] V14: suscripciones + historial_planes
- [ ] SuscripcionService
- [ ] Endpoints /api/admin/** [ADMIN only]
- [ ] Tests de PlanValidator
- [ ] Tests de WebhookController (con mocks de firma)
