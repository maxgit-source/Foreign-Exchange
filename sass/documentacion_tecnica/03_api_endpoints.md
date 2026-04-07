# API REST — Endpoints Completos

**Base URL:** `http://localhost:8080`
**Documentación interactiva:** `http://localhost:8080/swagger-ui.html`
**OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

## Autenticación

Todos los endpoints protegidos requieren:
```
Authorization: Bearer <accessToken>
```

---

## Módulo Auth (`/api/auth`)

### POST /api/auth/register
Registra un nuevo negocio (tenant) y crea su usuario OWNER.

**Request:**
```json
{
  "nombre": "Juan Pérez",
  "email": "juan@miempresa.com",
  "password": "minseguro123",
  "tenantNombre": "Mi Peluquería",
  "tenantEmail": "info@mipeluqueria.com"
}
```

**Response 201:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid-raw-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "usuarioId": 1,
  "nombre": "Juan Pérez",
  "email": "juan@miempresa.com",
  "role": "OWNER",
  "tenantId": 1,
  "tenantNombre": "Mi Peluquería",
  "tenantSlug": "mi-peluqueria"
}
```

**Validaciones:**
- email único global (409 si ya existe)
- tenantEmail único (409 si ya existe)
- password mínimo 8 caracteres

---

### POST /api/auth/login
```json
{ "email": "juan@miempresa.com", "password": "minseguro123" }
```
**Response 200:** mismo formato AuthResponseDTO

---

### POST /api/auth/refresh
```json
{ "refreshToken": "uuid-raw-token" }
```
**Response 200:** nuevo AuthResponseDTO (token rotation — el token anterior queda revocado)

---

### POST /api/auth/logout
**Headers:** Authorization: Bearer <token>
**Response 204 No Content**
(revoca todos los refresh tokens del usuario)

---

## Módulo Turnos (`/api/turnos`)
> Todos requieren autenticación. Roles: OWNER y STAFF pueden crear/modificar.

### GET /api/turnos
Lista paginada con filtros opcionales.

**Query params:**
| Param | Tipo | Descripción |
|---|---|---|
| fechaInicio | ISO DateTime | Desde esta fecha |
| fechaFin | ISO DateTime | Hasta esta fecha |
| profesionalId | Long | Filtrar por profesional |
| clienteId | Long | Filtrar por cliente |
| estado | TurnoStatus | PENDIENTE, CONFIRMADO, etc. |
| page | Integer | Página (default 0) |
| size | Integer | Tamaño (default 20) |

**Response 200:**
```json
{
  "content": [ TurnoResponseDTO ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

---

### GET /api/turnos/agenda?fecha=2025-04-05
Agenda del día completa, ordenada por hora de inicio.
**Response 200:** `[ TurnoResponseDTO ]`

---

### GET /api/turnos/{id}
**Response 200:** `TurnoResponseDTO`
**Response 404:** si no existe o no pertenece al tenant

---

### POST /api/turnos `[OWNER, STAFF]`
**Request:**
```json
{
  "clienteId": 5,
  "profesionalId": 3,
  "servicioId": 2,
  "fechaHoraInicio": "2025-04-10T10:00:00",
  "notas": "Cliente prefiere agua fría"
}
```

**Validaciones:**
- Servicio debe estar activo
- Fecha no puede ser en el pasado
- No solapamiento con otros turnos del profesional
- fechaHoraFin = fechaHoraInicio + servicio.duracionMinutos

**Response 201:** `TurnoResponseDTO`

---

### PATCH /api/turnos/{id}/confirmar `[OWNER, STAFF]`
Solo desde estado PENDIENTE → CONFIRMADO
**Response 200:** `TurnoResponseDTO`
**Response 409:** si estado inválido para transición

---

### PATCH /api/turnos/{id}/cancelar `[OWNER, STAFF]`
Desde PENDIENTE o CONFIRMADO → CANCELADO
Envía email de cancelación al cliente (async).
**Response 200:** `TurnoResponseDTO`

---

### PATCH /api/turnos/{id}/completar `[OWNER, STAFF]`
**Response 200:** `TurnoResponseDTO`

---

### PATCH /api/turnos/{id}/no-show `[OWNER, STAFF]`
**Response 200:** `TurnoResponseDTO`

**TurnoResponseDTO (completo):**
```json
{
  "id": 42,
  "clienteId": 5,
  "nombreCliente": "María García",
  "emailCliente": "maria@email.com",
  "telefonoCliente": "+54911234567",
  "profesionalId": 3,
  "nombreProfesional": "Dr. Pérez",
  "servicioId": 2,
  "nombreServicio": "Corte de cabello",
  "duracionMinutos": 45,
  "precioServicio": 2500.00,
  "fechaHoraInicio": "2025-04-10T10:00:00",
  "fechaHoraFin": "2025-04-10T10:45:00",
  "estado": "PENDIENTE",
  "notas": "Cliente prefiere agua fría",
  "createdAt": "2025-04-05T09:00:00"
}
```

---

## Módulo Clientes (`/api/clientes`)

### GET /api/clientes
**Query params:** `busqueda` (string, filtra nombre/apellido/email/teléfono), `page`, `size`
**Response 200:** Page<ClienteResponseDTO>

### GET /api/clientes/{id}
**Response 200:** `ClienteResponseDTO`

### GET /api/clientes/{id}/historial
**Response 200:** `[ TurnoResponseDTO ]` — historial desc por fecha

### POST /api/clientes
```json
{
  "nombre": "María",
  "apellido": "García",
  "email": "maria@email.com",
  "telefono": "+54911234567",
  "notas": "Alérgica al amonio"
}
```
**Response 201:** `ClienteResponseDTO`

### PUT /api/clientes/{id}
**Response 200:** `ClienteResponseDTO`

### DELETE /api/clientes/{id}
Soft delete (activo = false). **Response 204 No Content**

---

## Módulo Servicios (`/api/servicios`)

### GET /api/servicios
Lista servicios activos del tenant.
**Response 200:** `[ ServicioResponseDTO ]`

### GET /api/servicios/todos `[OWNER]`
Incluye inactivos.
**Response 200:** `[ ServicioResponseDTO ]`

### GET /api/servicios/{id}
**Response 200:** `ServicioResponseDTO`

### POST /api/servicios `[OWNER]`
```json
{
  "nombre": "Corte de cabello",
  "descripcion": "Corte con tijera y navaja",
  "duracionMinutos": 45,
  "precio": 2500.00,
  "activo": true
}
```
**Response 201:** `ServicioResponseDTO`

### PUT /api/servicios/{id} `[OWNER]`
**Response 200:** `ServicioResponseDTO`

### DELETE /api/servicios/{id} `[OWNER]`
Soft delete. **Response 204 No Content**

---

## Módulo Disponibilidad (`/api/disponibilidad`)

### GET /api/disponibilidad/{profesionalId}
Disponibilidad semanal del profesional.
**Response 200:**
```json
[
  {
    "id": 1,
    "dia": "LUNES",
    "horaInicio": "09:00",
    "horaFin": "13:00",
    "activo": true
  },
  {
    "id": 2,
    "dia": "LUNES",
    "horaInicio": "14:00",
    "horaFin": "18:00",
    "activo": true
  }
]
```

### PUT /api/disponibilidad/{profesionalId} `[OWNER]`
Reemplaza **toda** la disponibilidad del profesional.
**Request:**
```json
[
  { "dia": "LUNES", "horaInicio": "09:00", "horaFin": "13:00" },
  { "dia": "LUNES", "horaInicio": "14:00", "horaFin": "18:00" },
  { "dia": "MIERCOLES", "horaInicio": "09:00", "horaFin": "17:00" }
]
```
**Response 200:** `[ DisponibilidadResponseDTO ]`

### GET /api/disponibilidad/{profesionalId}/slots
Calcula slots libres para una fecha y servicio.
**Query params:** `servicioId` (required), `fecha` (YYYY-MM-DD, required)
**Response 200:**
```json
[
  { "horaInicio": "09:00", "horaFin": "09:45" },
  { "horaInicio": "09:45", "horaFin": "10:30" },
  { "horaInicio": "10:30", "horaFin": "11:15" }
]
```

---

## Módulo Staff (`/api/staff`)

### GET /api/staff
Lista profesionales activos del tenant (OWNER + STAFF habilitados).
**Response 200:** `[ StaffResponseDTO ]`
```json
[
  {
    "id": 3,
    "nombre": "Dr. Pérez",
    "email": "dr.perez@clinica.com",
    "role": "STAFF",
    "enabled": true,
    "createdAt": "2025-01-15T10:00:00"
  }
]
```

> **FALTA:** POST, PUT, DELETE para gestión de staff.

---

## Módulo Público (`/public/{slug}`)
> **Sin autenticación** — acceso desde widgets externos / landing page del negocio.

### GET /public/{slug}
Info pública del tenant (para personalizar el widget de booking).
**Response 200:**
```json
{
  "nombre": "Mi Peluquería",
  "slug": "mi-peluqueria",
  "telefono": "+5491112345678",
  "logoUrl": "https://cdn.example.com/logo.png",
  "colorPrimario": "#4F46E5",
  "timezone": "America/Argentina/Buenos_Aires"
}
```

### GET /public/{slug}/servicios
**Response 200:** `[ ServicioResponseDTO ]`

### GET /public/{slug}/profesionales
**Response 200:**
```json
[
  { "id": 3, "nombre": "Dr. Pérez" }
]
```
*(Solo id y nombre — sin email ni datos internos)*

### GET /public/{slug}/slots?profesionalId=3&servicioId=2&fecha=2025-04-10
**Response 200:** `[ SlotDisponibleDTO ]`

### POST /public/{slug}/reservar
Crea un turno desde el widget público. Auto-crea el cliente si no existe.
```json
{
  "profesionalId": 3,
  "servicioId": 2,
  "fechaHoraInicio": "2025-04-10T10:00:00",
  "nombreCliente": "Pedro",
  "apellidoCliente": "López",
  "emailCliente": "pedro@email.com",
  "telefonoCliente": "+5491187654321",
  "notas": ""
}
```
**Response 201:** `TurnoResponseDTO`
Envía email de confirmación al cliente.

---

## Endpoints de Sistema

| Endpoint | Descripción |
|---|---|
| GET /actuator/health | Health check (sin auth) |
| GET /swagger-ui.html | Documentación interactiva |
| GET /v3/api-docs | OpenAPI JSON spec |

---

## Códigos de Error Estandarizados

Todos los errores devuelven:
```json
{
  "timestamp": "2025-04-05T09:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Turno no encontrado con id: 99",
  "path": "/api/turnos/99"
}
```

| Código | Excepción | Cuándo |
|---|---|---|
| 400 | MethodArgumentNotValid | Validación de DTO fallida |
| 401 | UnauthorizedException | Token inválido/expirado |
| 403 | AccessDeniedException | Rol insuficiente |
| 404 | ResourceNotFoundException | Recurso no encontrado o fuera del tenant |
| 409 | ConflictException | Email duplicado, solapamiento de turno |
| 422 | BusinessException | Regla de negocio violada (estado inválido, fecha pasada, etc.) |
| 500 | Exception | Error interno (logueado con stack trace) |

---

## Endpoints FALTANTES (a implementar)

| Endpoint | Módulo | Fase |
|---|---|---|
| POST /api/staff | Staff CRUD | FASE 1 |
| PUT /api/staff/{id} | Staff CRUD | FASE 1 |
| DELETE /api/staff/{id} | Staff CRUD | FASE 1 |
| POST /api/staff/invitar | Invitación por email | FASE 1 |
| PATCH /api/turnos/{id}/reprogramar | Reschedule | FASE 2 |
| POST /api/disponibilidad/{id}/bloquear | Fechas bloqueadas | FASE 2 |
| GET /api/reportes/dashboard | KPIs | FASE 4 |
| GET /api/reportes/ingresos | Revenue report | FASE 4 |
| POST /api/pagos/checkout | Pago de turno | FASE 3 |
| GET /api/admin/** | Superadmin panel | FASE 5 |
| GET /api/categorias | Categorías de servicios | FASE 2 |
