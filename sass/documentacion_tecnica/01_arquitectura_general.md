# Arquitectura General — TuNegocio SaaS API

## Resumen Ejecutivo

**TuNegocio** es una plataforma SaaS multi-tenant diseñada para gestionar negocios de servicios. El backend actual cubre el verticale de **turnos/citas** (agenda, disponibilidad, clientes, servicios, reservas públicas). La arquitectura está diseñada para escalar hacia e-commerce, sistemas financieros y eventualmente capacidades de IA.

---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Framework | Spring Boot | 3.3.4 |
| Lenguaje | Java | 17 (LTS) |
| ORM | Spring Data JPA + Hibernate | via Spring Boot |
| Base de datos | PostgreSQL | 14+ |
| Migraciones | Flyway | latest |
| Seguridad | Spring Security + JJWT | 0.11.5 |
| Documentación API | SpringDoc OpenAPI | 2.6.0 |
| Email | Spring Mail (Jakarta Mail) | via Spring Boot |
| Build | Maven | 3.x |
| Contenedores | Docker + Docker Compose | — |
| Boilerplate | Lombok | latest |

---

## Estructura de Paquetes

```
com.tunegocio.turnosapi/
├── TurnosApiApplication.java          ← Entry point (@SpringBootApplication)
│
├── config/                            ← Configuración Spring
│   ├── ApplicationConfig.java         ← UserDetailsService, AuthManager, PasswordEncoder, @EnableAsync
│   ├── SecurityConfig.java            ← SecurityFilterChain, rutas públicas/privadas
│   ├── CorsConfig.java                ← CORS origins configurable por env
│   ├── JpaAuditingConfig.java         ← @EnableJpaAuditing
│   └── OpenApiConfig.java             ← Swagger UI con JWT Bearer Auth
│
├── controller/                        ← REST Controllers (HTTP layer)
│   ├── AuthController.java            ← /api/auth (register, login, refresh, logout)
│   ├── TurnoController.java           ← /api/turnos
│   ├── ClienteController.java         ← /api/clientes
│   ├── ServicioController.java        ← /api/servicios
│   ├── DisponibilidadController.java  ← /api/disponibilidad
│   ├── StaffController.java           ← /api/staff
│   └── PublicBookingController.java   ← /public/{slug} (sin auth)
│
├── entity/                            ← JPA Entities + Enums
│   ├── BaseEntity.java                ← Abstract: createdAt, updatedAt (@CreatedDate/@LastModifiedDate)
│   ├── Tenant.java                    ← Negocio/empresa (multitenancy root)
│   ├── Usuario.java                   ← Usuario de la plataforma (implementa UserDetails)
│   ├── Cliente.java                   ← Cliente del negocio
│   ├── Servicio.java                  ← Catálogo de servicios
│   ├── Turno.java                     ← Cita/turno
│   ├── DisponibilidadProfesional.java ← Bloques semanales de disponibilidad
│   ├── RefreshToken.java              ← Token de sesión (solo hash SHA-256)
│   ├── Role.java (enum)               ← ADMIN, OWNER, STAFF, CLIENT
│   ├── Plan.java (enum)               ← FREE, BASIC, PRO, ENTERPRISE
│   ├── TurnoStatus.java (enum)        ← PENDIENTE, CONFIRMADO, COMPLETADO, CANCELADO, NO_SHOW
│   └── DiaSemana.java (enum)          ← LUNES..DOMINGO (con conversión DayOfWeek)
│
├── repository/                        ← Spring Data JPA Repositories
│   ├── TurnoRepository.java           ← + JpaSpecificationExecutor
│   ├── ClienteRepository.java
│   ├── UsuarioRepository.java
│   ├── ServicioRepository.java
│   ├── DisponibilidadRepository.java
│   ├── TenantRepository.java
│   └── RefreshTokenRepository.java
│
├── service/                           ← Lógica de negocio
│   ├── AuthService.java
│   ├── TurnoService.java              ← Core business (validaciones, solapamiento, estados)
│   ├── ClienteService.java
│   ├── ServicioService.java
│   ├── DisponibilidadService.java     ← Cálculo de slots disponibles
│   ├── JwtService.java
│   ├── RefreshTokenService.java
│   └── NotificacionService.java       ← @Async email (confirmación, cancelación, recordatorio)
│
├── dto/                               ← Request / Response DTOs
│   ├── auth/                          ← LoginRequest, RegisterRequest, RefreshTokenRequest, AuthResponseDTO
│   ├── turno/                         ← TurnoRequestDTO, TurnoResponseDTO
│   ├── cliente/                       ← ClienteRequestDTO, ClienteResponseDTO
│   ├── servicio/                      ← ServicioRequestDTO, ServicioResponseDTO
│   ├── disponibilidad/                ← DisponibilidadResponseDTO, DisponibilidadUpsertDTO, SlotDisponibleDTO
│   ├── public/                        ← TenantPublicoDTO, ProfesionalPublicoDTO, ReservaPublicaRequestDTO
│   └── staff/                         ← StaffResponseDTO
│
├── exception/                         ← Manejo de errores
│   ├── ResourceNotFoundException.java ← 404
│   ├── ConflictException.java         ← 409
│   ├── BusinessException.java         ← 422
│   ├── UnauthorizedException.java     ← 401
│   ├── ErrorResponse.java             ← Formato JSON uniforme de error
│   └── GlobalExceptionHandler.java    ← @RestControllerAdvice
│
└── specification/                     ← JPA Criteria API
    └── TurnoSpecification.java        ← Filtros dinámicos: tenant, fecha, profesional, estado
```

---

## Estrategia Multi-Tenant

La plataforma usa **tenant isolation por columna** (shared database, shared schema):

- Cada tabla de datos tiene `tenant_id` como FK a la tabla `tenants`
- Toda query en services filtra por `tenant_id` del usuario autenticado
- El JWT incluye el claim `tenantId`, que se inyecta en el contexto de Spring Security
- Los usuarios son globales (email único en toda la plataforma), pero sus datos pertenecen a un tenant

```
tenants (1) ──────────────────────┐
                                   ▼
usuarios (N) → pertenecen a tenant, email global único
clientes (N) → aislados por tenant (mismo email válido en distintos tenants)
servicios (N) → aislados por tenant
turnos (N) → aislados por tenant
disponibilidad_profesional (N) → aislada por tenant
```

---

## Flujo de Autenticación

```
Client ──POST /api/auth/login──▶ AuthController
                                      │
                                      ▼
                               AuthService.authenticate()
                                      │
                         ┌────────────┴────────────┐
                         ▼                         ▼
                  JwtService                RefreshTokenService
                  (Access Token,            (UUID raw → SHA-256 hash en DB)
                   1 hora)                  (7 días)
                         │
                         ▼
                  AuthResponseDTO
                  { accessToken, refreshToken, role, tenantId, ... }
```

**Cada request protegido:**
```
Authorization: Bearer <accessToken>
       │
       ▼
JwtAuthenticationFilter
       │
       ▼ extrae email + tenantId del JWT
       │
       ▼
UsuarioRepository.findByEmail()
       │
       ▼
SecurityContextHolder (Usuario como UserDetails)
       │
       ▼
Controller → @PreAuthorize("hasRole('OWNER')") etc.
```

---

## Modelo de Planes

| Plan | Límite actual | Estado |
|---|---|---|
| FREE | Sin límite (NO enforcement) | DEFINIDO, no implementado |
| BASIC | Sin límite (NO enforcement) | DEFINIDO, no implementado |
| PRO | Sin límite (NO enforcement) | DEFINIDO, no implementado |
| ENTERPRISE | Sin límite (NO enforcement) | DEFINIDO, no implementado |

> **Critico:** El campo `plan` existe en la entidad `Tenant` pero ningún service valida límites según el plan. Esto es una deuda técnica de alta prioridad.

---

## Comunicaciones Asíncronas

`NotificacionService` usa `@Async` (Spring TaskExecutor):
- Confirmación de turno → email con HTML branded (color primario del tenant)
- Cancelación de turno → email al cliente
- Recordatorio de turno → método definido pero **nunca invocado** (sin scheduler)

---

## Docker Setup

```yaml
# docker-compose.yml (estado actual)
services:
  postgres-db:
    image: postgres:14
    environment: POSTGRES_DB=turnos_db, POSTGRES_USER, POSTGRES_PASSWORD
    volumes: postgres_data:/var/lib/postgresql/data

  api:
    build: .
    ports: "8080:8080"
    depends_on: postgres-db
    environment: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, MAIL_*, CORS_ORIGINS
```

---

## Puntos Fuertes del Diseño Actual

1. **Seguridad robusta:** JWT + Refresh Token Rotation con hashing SHA-256
2. **Aislamiento de tenant:** Consistente en todas las queries
3. **Validación Jakarta:** DTOs con @Valid aplicado en controllers
4. **Estado de Turno como máquina de estados:** Transiciones validadas
5. **Disponibilidad flexible:** Múltiples bloques por día, cálculo de slots configurable
6. **Reserva pública sin auth:** Widget de booking para clientes finales
7. **Emails async:** No bloquean el request principal
8. **Flyway:** Migraciones versionadas, reproducibles
9. **Swagger UI:** Documentación auto-generada con auth Bearer

---

## Debilidades Arquitectónicas Actuales

1. Sin tests (0% cobertura)
2. Sin rate limiting
3. Sin caching
4. Sin audit logging
5. Sin enforcement de planes
6. Staff CRUD ausente (solo GET)
7. Sin scheduler para recordatorios
8. Sin métricas / observabilidad
