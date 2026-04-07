# Estrategia Multi-Tenancy y Bases de Datos

## El Problema del Diseño Actual

El sistema actual usa **shared schema** — todos los tenants comparten las mismas tablas con `tenant_id` como filtro:

```sql
-- Todos mezclados en la misma tabla
SELECT * FROM turnos WHERE tenant_id = 5;
SELECT * FROM turnos WHERE tenant_id = 12;
```

**Límites de este enfoque a medida que crece:**
- Un tenant con millones de registros degrada las queries de todos los demás (noisy neighbor)
- Un bug que omita el filtro `tenant_id` expone datos de todos los clientes
- No podés hacer backup/restore individual por cliente
- Empresas con compliance estricto (salud, finanzas, legal) no aceptan datos compartidos
- Imposible ofrecer "tu propia instancia" como feature de plan Enterprise

---

## Las Tres Estrategias de Multi-Tenancy

### Estrategia 1: Shared Schema (actual)
```
PostgreSQL
└── public schema
    ├── turnos      (tenant_id=1, tenant_id=2, tenant_id=3 mezclados)
    ├── clientes    (tenant_id=1, tenant_id=2...)
    └── servicios   (...)
```
- Usado en: startups tempranas, herramientas internas
- Límite: ~500 tenants activos antes de degradación perceptible

### Estrategia 2: Schema por Tenant ← RECOMENDADA
```
PostgreSQL
├── tenant_mi_peluqueria schema
│   ├── turnos
│   ├── clientes
│   └── servicios
├── tenant_clinica_norte schema
│   ├── turnos
│   ├── clientes
│   └── servicios
└── public schema
    ├── tenants     (registro global)
    └── usuarios    (autenticación global)
```
- Un PostgreSQL, N schemas
- Aislamiento real sin costo de infraestructura extra
- Migraciones Flyway corren por schema
- Backup por schema = backup por cliente

### Estrategia 3: Base de Datos por Tenant
```
empresa-grande-1 → PostgreSQL DB propia
empresa-grande-2 → PostgreSQL DB propia
tenants-small   → PostgreSQL DB compartida (pool)
```
- Para clientes Enterprise con compliance estricto
- Operacionalmente costoso (N connection pools)
- Implementar solo bajo demanda

---

## Estrategia por Plan (la solución inteligente)

No todos los clientes necesitan el mismo nivel de aislamiento:

```
FREE / BASIC     → shared schema (pool de tenants pequeños)
PRO              → schema dedicado en DB compartida
ENTERPRISE       → schema dedicado O DB propia (según contrato)
```

Esto permite escalar el modelo de negocio sin sobrecomplicar la infraestructura desde el día uno.

---

## Implementación: Schema-per-Tenant en Spring Boot

### 1. TenantContext (hilo local)
```java
// Guarda el tenant del request actual en ThreadLocal
public class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    
    public static void setTenant(String tenantSlug) { CURRENT.set(tenantSlug); }
    public static String getTenant() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
```

### 2. TenantRoutingDataSource
```java
// Spring llama a determineCurrentLookupKey() antes de cada query
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenant();
    }
}
```

### 3. Setear el schema en cada request (vía JwtAuthenticationFilter)
```java
// Al validar el JWT, setear el schema correspondiente
String tenantSlug = jwtService.extractTenantSlug(token); // nuevo claim en JWT
TenantContext.setTenant("tenant_" + tenantSlug);

// Al finalizar el request, limpiar
// (en un Filter con finally block)
TenantContext.clear();
```

### 4. SchemaCreationService (al registrar un nuevo tenant)
```java
@Service
public class SchemaService {
    
    @Autowired DataSource dataSource;
    
    @Transactional
    public void crearSchema(String tenantSlug) {
        String schema = "tenant_" + tenantSlug;
        
        try (Connection conn = dataSource.getConnection()) {
            // Crear el schema
            conn.createStatement().execute(
                "CREATE SCHEMA IF NOT EXISTS " + schema
            );
            
            // Correr migraciones Flyway en el nuevo schema
            Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration/tenant")  // ← migraciones de tenant
                .load();
            
            flyway.migrate();
        }
    }
    
    public void eliminarSchema(String tenantSlug) {
        // Solo para tenants dados de baja definitivamente
        // Hacer backup primero
        String schema = "tenant_" + tenantSlug;
        conn.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
    }
}
```

### 5. Migraciones separadas: global vs tenant

```
resources/db/migration/
├── global/              ← migraciones de tablas globales (tenants, usuarios)
│   ├── V1__create_tenants.sql
│   └── V2__create_usuarios.sql
└── tenant/              ← migraciones que se replican en CADA schema
    ├── V1__create_servicios_clientes.sql
    ├── V2__create_disponibilidad.sql
    ├── V3__create_turnos.sql
    └── V4__create_refresh_tokens.sql
```

**Regla:** Lo que es global (auth, billing, plataforma) va en `public`. Lo que pertenece al negocio (turnos, clientes, servicios) va en el schema del tenant.

### 6. Simplificación: ya no necesitás tenant_id en las tablas de tenant
```sql
-- ANTES (shared schema)
CREATE TABLE turnos (
    id BIGSERIAL,
    tenant_id BIGINT NOT NULL,   ← ya no hace falta
    cliente_id BIGINT,
    ...
);

-- DESPUÉS (schema por tenant)
CREATE TABLE tenant_mipeluqueria.turnos (
    id BIGSERIAL,
    -- tenant_id no existe, el schema YA es el tenant
    cliente_id BIGINT,
    ...
);
```

Esto simplifica TODAS las queries (sin filtro `tenant_id`), reduce el tamaño de índices, y elimina toda posibilidad de data leak cross-tenant.

---

## Polyglot Persistence — La herramienta correcta para cada dato

Como referencia, así distribuye Netflix sus datos por tipo:

```
TIPO DE DATO                TECNOLOGÍA          RAZÓN
─────────────────────────────────────────────────────────────────────
Transaccional (turnos,      PostgreSQL          ACID, relaciones, JOINs
clientes, pedidos, pagos)

Sesiones, caché,            Redis               Microsegundos, TTL nativo
rate limiting, tokens

Búsqueda de texto           Elasticsearch       Full-text, facets, fuzzy
(productos, clientes)

Actividad, logs, eventos    ClickHouse /        Columnar, append-only,
                            TimescaleDB         analítica rápida

Archivos (imágenes,         S3 / Cloudinary     CDN, transformaciones
documentos, logos)

Notificaciones en           Redis Pub/Sub /     Push en tiempo real
tiempo real                 WebSockets

Grafos / relaciones         Neo4j               Recomendaciones, redes
complejas
```

### Para TuNegocio, el stack de datos recomendado por etapa:

```
ETAPA 1 (ahora)
───────────────
PostgreSQL (schema-per-tenant)   → TODO
Redis                            → caché + rate limiting

ETAPA 2 (con e-commerce y búsqueda)
─────────────────────────────────────
+ Elasticsearch                  → búsqueda de productos y clientes
+ S3 / Cloudinary                → imágenes de productos y staff

ETAPA 3 (con analytics avanzado)
──────────────────────────────────
+ ClickHouse o TimescaleDB        → analytics de turnos e ingresos
+ Kafka                           → eventos entre servicios

ETAPA 4 (con IA)
─────────────────
+ pgvector (extensión PostgreSQL) → embeddings para búsqueda semántica
+ Redis Vector Search             → similitud en tiempo real
```

---

## Impacto en el Código Actual

### Qué cambia con schema-per-tenant:

| Componente | Cambio |
|---|---|
| Entities | Eliminar campo `tenant` y `@ManyToOne Tenant` de todas las entidades de negocio |
| Repositories | Eliminar todos los métodos `findByXxxAndTenant_Id()` — ya no necesarios |
| Services | Eliminar el filtrado por `tenant` — el schema lo hace implícitamente |
| JWT | Agregar claim `tenantSlug` en lugar de (o además de) `tenantId` |
| JwtAuthFilter | Setear `TenantContext.setTenant()` al validar el token |
| AuthService | Llamar a `SchemaService.crearSchema()` al registrar un nuevo tenant |
| SecurityConfig | Sin cambios |
| Controllers | Sin cambios |

### Tablas que se mueven a schema del tenant:
- `servicios`
- `clientes`
- `turnos`
- `disponibilidad_profesional`
- `refresh_tokens` (podría ir en global o en tenant)

### Tablas que quedan en `public` (global):
- `tenants`
- `usuarios`

---

## Por Qué Hacer Esto Ahora y No Después

Migrar de shared-schema a schema-per-tenant **con datos de producción existentes** es muy costoso:
1. Hay que mover filas de una tabla global a schemas individuales
2. Los IDs pueden chocar si varios tenants tienen el mismo `id` para un cliente
3. Todas las FKs entre tablas globales y de tenant se rompen

**Hacerlo ahora, con el sistema nuevo y sin datos de producción, cuesta 1 día de trabajo.**
Hacerlo después, con 500 tenants y millones de registros, puede costar semanas y requiere downtime planificado.
