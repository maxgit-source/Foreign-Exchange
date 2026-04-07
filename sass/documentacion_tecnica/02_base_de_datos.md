# Base de Datos — Esquema, Entidades y Migraciones

## Motor: PostgreSQL 14+

Flyway gestiona todas las migraciones. La estrategia JPA es `validate` (Flyway crea, JPA solo valida).

---

## Diagrama de Entidades (ERD)

```
┌─────────────┐        ┌──────────────────────────┐
│   tenants   │        │         usuarios          │
├─────────────┤  1:N   ├──────────────────────────┤
│ id (PK)     │◀───────│ id (PK)                  │
│ nombre      │        │ nombre                   │
│ slug        │        │ email (unique global)    │
│ email       │        │ password (bcrypt)        │
│ telefono    │        │ role (ADMIN/OWNER/STAFF/  │
│ direccion   │        │       CLIENT)            │
│ plan        │        │ tenant_id (FK)           │
│ activo      │        │ enabled                  │
│ timezone    │        │ created_at               │
│ logo_url    │        │ updated_at               │
│ color_prim  │        └──────────────────────────┘
│ created_at  │                    │ 1:N
│ updated_at  │                    ▼
└─────────────┘        ┌──────────────────────────┐
       │ 1:N           │  disponibilidad_prof.     │
       ├───────────────├──────────────────────────┤
       │               │ id (PK)                  │
       │               │ profesional_id (FK→usu.) │
       │               │ tenant_id (FK)           │
       │               │ dia (enum DiaSemana)     │
       │               │ hora_inicio (TIME)       │
       │               │ hora_fin (TIME)          │
       │               │ activo                   │
       │               │ created_at               │
       │               └──────────────────────────┘
       │
       ├───────────────┐
       │               ▼
       │       ┌───────────────┐
       │       │   servicios   │
       │       ├───────────────┤
       │       │ id (PK)       │
       │       │ tenant_id(FK) │
       │       │ nombre        │
       │       │ descripcion   │
       │       │ duracion_min  │
       │       │ precio(10,2)  │
       │       │ activo        │
       │       │ created_at    │
       │       │ updated_at    │
       │       └───────────────┘
       │               ▲ N:1
       │               │
       │       ┌───────────────────────────────────────────┐
       │       │                   turnos                  │
       ├───────├───────────────────────────────────────────┤
       │       │ id (PK)                                   │
       │       │ tenant_id (FK→tenants)                    │
       │       │ cliente_id (FK→clientes)                  │
       │       │ profesional_id (FK→usuarios)              │
       │       │ servicio_id (FK→servicios)                │
       │       │ fecha_hora_inicio (TIMESTAMP)             │
       │       │ fecha_hora_fin (TIMESTAMP)                │
       │       │ estado (PENDIENTE/CONFIRMADO/etc.)        │
       │       │ notas                                     │
       │       │ created_at                                │
       │       │ updated_at                                │
       │       └───────────────────────────────────────────┘
       │                                  ▲ N:1
       │                                  │
       └─────────────┐            ┌───────────────┐
                     ▼            │   clientes    │
             ┌──────────────┐     ├───────────────┤
             │refresh_tokens│     │ id (PK)       │
             ├──────────────┤     │ tenant_id(FK) │
             │ id (PK)      │     │ nombre        │
             │ usuario_id   │     │ apellido      │
             │   (FK→usu.)  │     │ email         │
             │ token_hash   │     │ telefono      │
             │   (SHA-256)  │     │ notas         │
             │ expires_at   │     │ activo        │
             │ revoked      │     │ created_at    │
             │ created_at   │     │ updated_at    │
             └──────────────┘     └───────────────┘
```

---

## Migraciones Flyway (detalle)

### V1 — Tenants y Usuarios

```sql
CREATE TABLE tenants (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(200) NOT NULL UNIQUE,
    telefono VARCHAR(30),
    direccion TEXT,
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    activo BOOLEAN DEFAULT TRUE,
    timezone VARCHAR(50) DEFAULT 'America/Argentina/Buenos_Aires',
    logo_url TEXT,
    color_primario VARCHAR(7),    -- hex color #RRGGBB
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE,    -- global único (cross-tenant)
    password VARCHAR(255) NOT NULL,        -- bcrypt hash
    role VARCHAR(20) NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### V2 — Servicios y Clientes

```sql
CREATE TABLE servicios (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
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
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100),
    email VARCHAR(200),
    telefono VARCHAR(30),
    notas TEXT,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (email, tenant_id)  -- mismo email válido en distintos tenants
);
```

### V3 — Disponibilidad Profesional

```sql
CREATE TABLE disponibilidad_profesional (
    id BIGSERIAL PRIMARY KEY,
    profesional_id BIGINT NOT NULL REFERENCES usuarios(id),
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    dia VARCHAR(20) NOT NULL,     -- LUNES, MARTES... DOMINGO
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### V4 — Turnos

```sql
CREATE TABLE turnos (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    profesional_id BIGINT NOT NULL REFERENCES usuarios(id),
    servicio_id BIGINT NOT NULL REFERENCES servicios(id),
    fecha_hora_inicio TIMESTAMP NOT NULL,
    fecha_hora_fin TIMESTAMP NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    notas TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Índices para performance
CREATE INDEX idx_turnos_tenant ON turnos(tenant_id);
CREATE INDEX idx_turnos_cliente ON turnos(cliente_id);
CREATE INDEX idx_turnos_profesional_fecha ON turnos(profesional_id, fecha_hora_inicio);
CREATE INDEX idx_turnos_fecha ON turnos(fecha_hora_inicio);
CREATE INDEX idx_turnos_estado ON turnos(estado);
-- Índice parcial para detección rápida de solapamiento (excluye cancelados)
CREATE INDEX idx_turnos_overlap ON turnos(profesional_id, fecha_hora_inicio, fecha_hora_fin)
    WHERE estado != 'CANCELADO';
```

### V5 — Refresh Tokens

```sql
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,    -- SHA-256 hex, nunca el token raw
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash) WHERE revoked = FALSE;
CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens(usuario_id);
```

---

## Enums

### Role
| Valor | Descripción |
|---|---|
| ADMIN | Superadministrador de la plataforma (gestión interna) |
| OWNER | Dueño del negocio (acceso total a su tenant) |
| STAFF | Profesional/empleado (puede gestionar turnos y clientes) |
| CLIENT | Cliente del negocio (acceso limitado, no usado en API actual) |

### Plan
| Valor | Límites (pendientes de implementar) |
|---|---|
| FREE | Gratis, límites bajos |
| BASIC | Suscripción básica |
| PRO | Suscripción profesional |
| ENTERPRISE | Ilimitado / personalizado |

### TurnoStatus (Máquina de estados)
```
PENDIENTE ──→ CONFIRMADO
PENDIENTE ──→ COMPLETADO
PENDIENTE ──→ CANCELADO
PENDIENTE ──→ NO_SHOW
CONFIRMADO ──→ COMPLETADO
CONFIRMADO ──→ CANCELADO
CONFIRMADO ──→ NO_SHOW
(COMPLETADO y NO_SHOW son estados finales)
```

### DiaSemana
`LUNES, MARTES, MIERCOLES, JUEVES, VIERNES, SABADO, DOMINGO`
(Con conversión desde/hacia `java.time.DayOfWeek`)

---

## Decisiones de Diseño

| Decisión | Razón |
|---|---|
| BIGSERIAL (autoincremental) vs UUID | Simplicidad y performance de índices. UUID recomendado para entornos distribuidos (migrar en FASE 5) |
| Soft delete (activo=false) | Preservar historial de turnos para clientes y servicios eliminados |
| email único global en usuarios | Un email = una cuenta en toda la plataforma |
| email único por tenant en clientes | El mismo cliente puede existir en múltiples negocios |
| token_hash SHA-256 en refresh_tokens | Nunca exponer credenciales en la DB |
| timezone en tenant | Cálculo correcto de slots y recordatorios por zona horaria del negocio |
| NUMERIC(10,2) en precio | Evitar errores de punto flotante en cálculos financieros |

---

## Migraciones Faltantes (a crear en Fases)

| Migración | Contenido |
|---|---|
| V6 | `staff_invitaciones` — invitaciones para agregar staff por email |
| V7 | `plan_limites` — tabla de límites configurables por plan |
| V8 | `audit_log` — log de cambios con usuario, timestamp, tabla, acción |
| V9 | `fechas_bloqueadas` — días no laborables por profesional o tenant |
| V10 | `pagos` — tabla de pagos y facturación de turnos |
| V11 | `productos` — catálogo de productos para e-commerce |
| V12 | `pedidos` y `pedido_items` — órdenes de compra |
| V13 | `categorias` — categorías de servicios/productos |
| V14 | `notificaciones_log` — log de emails/SMS enviados |
