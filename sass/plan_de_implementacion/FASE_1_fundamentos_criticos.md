# FASE 1 — Fundamentos Críticos (Backend)
**Objetivo:** Completar el backend actual para que sea apto para producción real.
**Duración estimada:** Hoy mismo (completar antes de tocar frontend)

> **Referencia:** Ver `documentacion_tecnica/07_estrategia_multitenancy_y_bases_de_datos.md` para el análisis completo de la estrategia.

---

## 1.0 — Migración a Schema-per-Tenant ⚠️ HACER PRIMERO

**Este paso va antes de todo lo demás.** Cambia la estructura de la DB y simplifica el código de todos los módulos siguientes. Hacerlo ahora, sin datos de producción, cuesta un día. Hacerlo después, con clientes reales, puede costar semanas.

### Por qué cambiar ahora

El sistema actual mete todos los tenants en las mismas tablas con `tenant_id`. Con schema-per-tenant:
- Cada negocio tiene su propio "espacio" en PostgreSQL aislado
- Las queries se simplifican (sin filtro `tenant_id` en cada una)
- Podés hacer backup por cliente
- Empresas con compliance (salud, finanzas) pueden contratar tranquilas

### Paso 1 — Nueva estructura de migraciones Flyway

Reorganizar `db/migration/` en dos carpetas:

```
resources/db/migration/
├── global/          ← tablas de plataforma (corren una vez en schema 'public')
│   ├── V1__create_tenants.sql
│   └── V2__create_usuarios.sql
└── tenant/          ← tablas de negocio (se replican en cada schema de tenant)
    ├── V1__create_servicios_clientes.sql
    ├── V2__create_disponibilidad.sql
    ├── V3__create_turnos.sql
    └── V4__create_refresh_tokens.sql
```

### Paso 2 — Reescribir las migraciones de tenant (sin tenant_id)

```sql
-- tenant/V1__create_servicios_clientes.sql
-- Ya no existe tenant_id — el schema ES el tenant

CREATE TABLE servicios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    descripcion TEXT,
    duracion_minutos INTEGER NOT NULL CHECK (duracion_minutos BETWEEN 5 AND 480),
    precio NUMERIC(10,2) NOT NULL CHECK (precio > 0),
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE clientes (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100),
    email VARCHAR(200),
    telefono VARCHAR(30),
    notas TEXT,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (email)   -- único dentro del schema (= único dentro del tenant)
);
```

### Paso 3 — TenantContext.java (nuevo archivo)

```java
package com.tunegocio.turnosapi.config;

public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    
    public static void setTenant(String tenantSlug) {
        CURRENT_TENANT.set("tenant_" + tenantSlug.replace("-", "_"));
    }
    
    public static String getTenant() {
        return CURRENT_TENANT.get();
    }
    
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

### Paso 4 — TenantSchemaInterceptor.java

```java
package com.tunegocio.turnosapi.config;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class TenantSchemaInterceptor {
    
    private final EntityManager em;
    
    @Before("@within(org.springframework.stereotype.Repository)")
    public void setSearchPath() {
        String schema = TenantContext.getTenant();
        if (schema != null) {
            em.createNativeQuery("SET search_path TO " + schema + ", public")
              .executeUpdate();
        }
    }
}
```

**Dependencia AOP en pom.xml:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### Paso 5 — Actualizar JwtAuthenticationFilter

```java
// Después de validar el token y cargar el usuario:
String tenantSlug = jwtService.extractTenantSlug(token);  // nuevo claim
TenantContext.setTenant(tenantSlug);

// Al final del filter (finally block):
TenantContext.clear();
```

**Actualizar JwtService para incluir tenantSlug en el token:**
```java
// En generateToken(), agregar claim:
.claim("tenantSlug", usuario.getTenant().getSlug())  // además de tenantId
```

### Paso 6 — SchemaService.java (se llama al registrar un tenant)

```java
@Service
@RequiredArgsConstructor
public class SchemaService {
    
    @Autowired
    private DataSource dataSource;
    
    public void crearSchemaParaTenant(String tenantSlug) {
        String schema = "tenant_" + tenantSlug.replace("-", "_");
        
        try (Connection conn = dataSource.getConnection()) {
            // Crear el schema
            conn.createStatement().execute(
                "CREATE SCHEMA IF NOT EXISTS " + schema
            );
            
            // Correr las migraciones de tenant en el nuevo schema
            Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration/tenant")
                .table("flyway_schema_history")   // tabla de control por schema
                .load();
            
            flyway.migrate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Error creando schema para tenant: " + tenantSlug, e);
        }
    }
}
```

**Llamar desde AuthService.register():**
```java
// Después de crear el Tenant y el Usuario OWNER:
schemaService.crearSchemaParaTenant(tenant.getSlug());
```

### Paso 7 — Simplificar las Entities (eliminar tenant)

```java
// ANTES
@Entity
public class Turno extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;           // ← eliminar
    // ...
}

// DESPUÉS
@Entity
public class Turno extends BaseEntity {
    // tenant_id no existe — el schema ya es el tenant
    @ManyToOne(fetch = FetchType.LAZY)
    private Cliente cliente;
    // ...
}
```

**Entidades a limpiar:** `Turno`, `Cliente`, `Servicio`, `DisponibilidadProfesional`

### Paso 8 — Simplificar los Services (eliminar filtros por tenant)

```java
// ANTES (TurnoService)
public TurnoResponseDTO obtenerPorId(Long id, Usuario usuario) {
    return turnoRepo.findByIdAndTenant_Id(id, usuario.getTenant().getId())
        .orElseThrow(() -> new ResourceNotFoundException("Turno", id));
}

// DESPUÉS — el schema lo aísla, findById es suficiente
public TurnoResponseDTO obtenerPorId(Long id) {
    return turnoRepo.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Turno", id));
}
```

**Repositories a simplificar:** `TurnoRepository`, `ClienteRepository`, `ServicioRepository`, `DisponibilidadRepository`

### Para el endpoint público (/public/{slug})

El slug en la URL ya identifica el tenant. Al entrar al controller:
```java
@GetMapping("/public/{slug}/servicios")
public ResponseEntity<?> serviciosPublicos(@PathVariable String slug) {
    Tenant tenant = tenantRepo.findBySlug(slug).orElseThrow(...);
    TenantContext.setTenant(slug);  // setear schema
    try {
        return ResponseEntity.ok(servicioService.listarActivos());
    } finally {
        TenantContext.clear();
    }
}
```

---

### Checklist 1.0 — Schema-per-Tenant

- [ ] Reorganizar `db/migration/` en `global/` y `tenant/`
- [ ] Reescribir migraciones de tenant sin columna `tenant_id`
- [ ] `TenantContext.java` (ThreadLocal con el slug del tenant)
- [ ] `TenantSchemaInterceptor.java` (AOP que setea search_path)
- [ ] Dependencia `spring-boot-starter-aop` en pom.xml
- [ ] `SchemaService.java` con Flyway por schema
- [ ] Llamar `schemaService.crearSchema()` en `AuthService.register()`
- [ ] Actualizar `JwtService` para incluir claim `tenantSlug`
- [ ] Actualizar `JwtAuthenticationFilter` para setear `TenantContext`
- [ ] Eliminar campo `tenant` de: Turno, Cliente, Servicio, DisponibilidadProfesional
- [ ] Simplificar repositories: eliminar métodos `findByXxxAndTenant_Id`
- [ ] Simplificar services: eliminar filtros `tenant.getId()`
- [ ] Actualizar `PublicBookingController` para setear `TenantContext` por slug
- [ ] Test manual: crear 2 tenants, verificar que cada uno ve solo sus datos

---

## 1.1 — Staff CRUD Completo

### Qué agregar

**Nueva migración: V6__create_staff_invitaciones.sql**
```sql
CREATE TABLE staff_invitaciones (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    email VARCHAR(200) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,    -- SHA-256 del link de invitación
    expires_at TIMESTAMP NOT NULL,
    usado BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(email, tenant_id)
);
```

**StaffController.java — endpoints a agregar:**
```java
POST   /api/staff                     → crear usuario STAFF (OWNER only)
PUT    /api/staff/{id}                → actualizar nombre, email, habilitado (OWNER)
DELETE /api/staff/{id}                → deshabilitar (enabled=false) (OWNER)
POST   /api/staff/invitar             → enviar invitación por email (OWNER)
GET    /api/staff/aceptar?token=...   → aceptar invitación (público, para el link del email)
```

**DTOs a crear:**
```java
// StaffCreateDTO
{
    "nombre": "Dr. García",
    "email": "garcia@clinica.com",
    "password": "seguro123"
}

// StaffUpdateDTO
{
    "nombre": "Dr. García López",
    "enabled": true
}

// StaffInvitacionDTO
{
    "email": "nuevo@profesional.com"
}
```

**StaffService.java — lógica:**
```java
public Usuario crear(StaffCreateDTO dto, Usuario owner) {
    // Validar que email no exista globalmente
    if (usuarioRepo.existsByEmail(dto.getEmail())) throw new ConflictException(...)
    // Crear usuario con role=STAFF, tenant=owner.getTenant()
    // Enviar email de bienvenida (NotificacionService async)
}

public Usuario actualizar(Long id, StaffUpdateDTO dto, Usuario owner) {
    // Buscar en tenant del owner
    // No permitir modificar el propio OWNER
    // Actualizar campos
}

public void deshabilitar(Long id, Usuario owner) {
    // enabled=false, NO borrar (preservar historial de turnos)
}
```

---

## 1.2 — Rate Limiting

### Dependencia a agregar en pom.xml
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
```

### Implementación como Filter

**RateLimitFilter.java:**
```java
@Component
@Order(1)  // Antes de JWT filter
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String ip = req.getRemoteAddr();
        String path = req.getRequestURI();
        
        Bandwidth limit = determinarLimite(path);
        Bucket bucket = buckets.computeIfAbsent(ip + path, k ->
            Bucket.builder().addLimit(limit).build()
        );
        
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.getWriter().write("{\"error\": \"Too Many Requests\"}");
        }
    }
    
    private Bandwidth determinarLimite(String path) {
        if (path.startsWith("/api/auth/login"))
            return Bandwidth.simple(5, Duration.ofMinutes(1));   // 5/min
        if (path.startsWith("/api/auth/register"))
            return Bandwidth.simple(3, Duration.ofMinutes(10));  // 3/10min
        if (path.contains("/reservar"))
            return Bandwidth.simple(20, Duration.ofMinutes(1));  // 20/min
        return Bandwidth.simple(100, Duration.ofMinutes(1));     // General
    }
}
```

### Registrar en SecurityConfig
```java
.addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
```

---

## 1.3 — Recordatorios de Turnos (Scheduler)

### Habilitar Spring Scheduling

**En ApplicationConfig.java (o nueva clase):**
```java
@EnableScheduling  // agregar esta anotación
```

**TurnoReminderScheduler.java:**
```java
@Component
@RequiredArgsConstructor
public class TurnoReminderScheduler {
    
    private final TurnoRepository turnoRepo;
    private final NotificacionService notificacionService;
    
    // Corre cada hora
    @Scheduled(fixedRate = 3_600_000)
    public void enviarRecordatorios() {
        LocalDateTime desde = LocalDateTime.now().plusHours(23);
        LocalDateTime hasta = LocalDateTime.now().plusHours(25);
        
        List<Turno> turnosMañana = turnoRepo
            .findByFechaHoraInicioBetweenAndEstadoIn(
                desde, hasta, 
                List.of(TurnoStatus.PENDIENTE, TurnoStatus.CONFIRMADO)
            );
        
        turnosMañana.forEach(t -> 
            notificacionService.enviarRecordatorio(t)  // ya está implementado, solo falta llamarlo
        );
    }
}
```

**Query a agregar en TurnoRepository:**
```java
List<Turno> findByFechaHoraInicioBetweenAndEstadoIn(
    LocalDateTime desde, LocalDateTime hasta, List<TurnoStatus> estados
);
```

---

## 1.4 — Audit Log

### Migración V7__create_audit_log.sql
```sql
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    usuario_id BIGINT,
    accion VARCHAR(20) NOT NULL,      -- CREATE, UPDATE, DELETE, LOGIN
    entidad VARCHAR(100) NOT NULL,    -- 'Turno', 'Cliente', 'Servicio'
    entidad_id BIGINT,
    detalle TEXT,                     -- JSON con los cambios
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant ON audit_log(tenant_id, created_at DESC);
```

### AuditLog Entity + Repository
```java
@Entity @Table(name="audit_log")
public class AuditLog {
    @Id @GeneratedValue Long id;
    Long tenantId;
    Long usuarioId;
    String accion;
    String entidad;
    Long entidadId;
    String detalle;       // JSON con detalles del cambio
    String ipAddress;
    LocalDateTime createdAt;
}
```

### AuditService.java (inyectado en TurnoService, ClienteService, etc.)
```java
@Service
public class AuditService {
    public void log(String accion, String entidad, Long entidadId, 
                    Usuario usuario, Object detalle) {
        AuditLog entry = new AuditLog();
        entry.setAccion(accion);
        entry.setEntidad(entidad);
        entry.setEntidadId(entidadId);
        entry.setUsuarioId(usuario.getId());
        entry.setTenantId(usuario.getTenant().getId());
        entry.setDetalle(toJson(detalle));
        auditRepo.save(entry);
    }
}
```

---

## 1.5 — Tests Críticos

### Estructura de tests a crear
```
src/test/java/com/tunegocio/turnosapi/
├── service/
│   ├── TurnoServiceTest.java          ← PRIORITARIO
│   ├── DisponibilidadServiceTest.java ← PRIORITARIO
│   └── AuthServiceTest.java
├── controller/
│   ├── AuthControllerTest.java
│   ├── TurnoControllerTest.java
│   └── PublicBookingControllerTest.java
└── repository/
    └── TurnoRepositoryTest.java       ← Test de solapamiento
```

### Tests críticos de TurnoService
```java
@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    @Test void crear_debeRechazarFechaPasada() { ... }
    @Test void crear_debeRechazarSolapamiento() { ... }
    @Test void crear_debeCalcularFechaFinCorrectamente() { ... }
    @Test void confirmar_soloDesde_PENDIENTE() { ... }
    @Test void cancelar_noSi_COMPLETADO() { ... }
    @Test void marcarNoShow_soloSi_PENDIENTE_o_CONFIRMADO() { ... }
    
    @Test void calcularSlots_debeExcluirTurnosExistentes() { ... }
    @Test void calcularSlots_debeExcluirSlotsPasadosSiEsHoy() { ... }
    @Test void calcularSlots_debeRetornarVacioSiNoHayDisponibilidad() { ... }
}
```

---

## 1.6 — Reprogramación de Turnos

**Nuevo endpoint:**
```
PATCH /api/turnos/{id}/reprogramar
```

**ReprogarTurnoDTO:**
```java
public record ReprogramarTurnoDTO(
    @NotNull LocalDateTime nuevaFechaHoraInicio
) {}
```

**Lógica en TurnoService.reprogramar():**
1. Solo desde PENDIENTE o CONFIRMADO
2. Nueva fecha no puede ser en el pasado
3. Calcular nueva `fechaHoraFin` = nuevaFechaHoraInicio + servicio.duracionMinutos
4. Validar solapamiento con el nuevo slot (excluyendo el turno actual)
5. Guardar, enviar email de reprogramación al cliente

---

## Checklist FASE 1

- [ ] V6 migración: staff_invitaciones
- [ ] StaffController: POST, PUT, DELETE, POST /invitar
- [ ] StaffService: crear, actualizar, deshabilitar, invitar
- [ ] StaffCreateDTO, StaffUpdateDTO, StaffInvitacionDTO
- [ ] RateLimitFilter (Bucket4j)
- [ ] TurnoReminderScheduler (@Scheduled)
- [ ] Query en TurnoRepository: findByFechaHoraInicioBetween...
- [ ] V7 migración: audit_log
- [ ] AuditLog entity + AuditLogRepository
- [ ] AuditService
- [ ] Tests: TurnoServiceTest (mínimo 6 tests)
- [ ] Tests: DisponibilidadServiceTest (mínimo 3 tests)
- [ ] Tests: AuthControllerTest (register, login, refresh, logout)
- [ ] ReprogramarTurnoDTO + endpoint PATCH /api/turnos/{id}/reprogramar
- [ ] Email de reprogramación en NotificacionService
