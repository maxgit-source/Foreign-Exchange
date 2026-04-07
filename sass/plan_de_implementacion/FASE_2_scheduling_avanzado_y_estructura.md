# FASE 2 — Scheduling Avanzado y Estructura de Negocio
**Objetivo:** Completar el verticale de turnos/agenda con todas las funcionalidades profesionales.
**Cuando ejecutar:** Después de FASE 1 completa.

---

## 2.1 — Fechas Bloqueadas / Días No Laborables

Un profesional puede marcar días o rangos como no disponibles (vacaciones, feriados, etc.).

### Migración V8__create_fechas_bloqueadas.sql
```sql
CREATE TABLE fechas_bloqueadas (
    id BIGSERIAL PRIMARY KEY,
    profesional_id BIGINT REFERENCES usuarios(id),  -- NULL = aplica a todo el tenant
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    motivo VARCHAR(200),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_fechas_bloqueadas_prof ON fechas_bloqueadas(profesional_id, fecha_inicio, fecha_fin);
CREATE INDEX idx_fechas_bloqueadas_tenant ON fechas_bloqueadas(tenant_id, fecha_inicio, fecha_fin);
```

### Endpoints
```
GET    /api/disponibilidad/{profesionalId}/bloqueados      → listar fechas bloqueadas
POST   /api/disponibilidad/{profesionalId}/bloquear        → crear bloqueo [OWNER]
DELETE /api/disponibilidad/bloqueados/{id}                 → eliminar bloqueo [OWNER]
```

### FechaBloqueadaDTO
```java
public record FechaBloqueadaRequestDTO(
    @NotNull LocalDate fechaInicio,
    @NotNull LocalDate fechaFin,
    String motivo
) {}
```

### Integración en DisponibilidadService.calcularSlots()
```java
// Paso adicional en calcularSlots():
boolean estaBloqueado = fechaBloqueadaRepo.existsBloqueoParaProfesionalEnFecha(
    profesionalId, fecha
);
if (estaBloqueado) return List.of();  // Sin slots disponibles
```

---

## 2.2 — Categorías de Servicios

### Migración V9__create_categorias.sql
```sql
CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    orden INTEGER DEFAULT 0,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

ALTER TABLE servicios ADD COLUMN categoria_id BIGINT REFERENCES categorias(id);
```

### Endpoints
```
GET    /api/categorias          → listar categorías del tenant
POST   /api/categorias          → crear categoría [OWNER]
PUT    /api/categorias/{id}     → actualizar [OWNER]
DELETE /api/categorias/{id}     → desactivar [OWNER]
```

### Actualizar endpoint de servicios
`GET /api/servicios` puede filtrar por `?categoriaId=X`

---

## 2.3 — Imágenes en Servicios y Perfil de Staff

### Dependencia: Cloudinary (o AWS S3)
```xml
<!-- Cloudinary -->
<dependency>
    <groupId>com.cloudinary</groupId>
    <artifactId>cloudinary-http45</artifactId>
    <version>1.38.0</version>
</dependency>
```

### Migración
```sql
ALTER TABLE servicios ADD COLUMN imagen_url TEXT;
ALTER TABLE usuarios ADD COLUMN foto_url TEXT;
```

### Endpoints
```
POST /api/servicios/{id}/imagen    → subir imagen del servicio [OWNER]
POST /api/staff/{id}/foto          → subir foto de perfil [OWNER o mismo STAFF]
```

### UploadService.java
```java
@Service
public class UploadService {
    
    @Value("${cloudinary.cloud-name}") String cloudName;
    // ...
    
    public String upload(MultipartFile file, String folder) {
        // Validar tipo (image/jpeg, image/png, image/webp)
        // Validar tamaño (max 5MB)
        // Subir a Cloudinary con transformación (max 800x800, webp)
        // Retornar URL pública
    }
}
```

---

## 2.4 — Historial de Cambios de Turno

Cuando un turno se reprograma o cambia de estado, mantener historial.

### Migración V10__create_turno_historial.sql
```sql
CREATE TABLE turno_historial (
    id BIGSERIAL PRIMARY KEY,
    turno_id BIGINT NOT NULL REFERENCES turnos(id),
    estado_anterior VARCHAR(20),
    estado_nuevo VARCHAR(20) NOT NULL,
    usuario_id BIGINT REFERENCES usuarios(id),    -- quién hizo el cambio (NULL si fue cliente)
    fecha_anterior TIMESTAMP,
    fecha_nueva TIMESTAMP,
    notas TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
```

Actualizar `TurnoService` para insertar en `turno_historial` en cada cambio de estado o reprogramación.

### Endpoint
```
GET /api/turnos/{id}/historial → [ TurnoHistorialDTO ]
```

---

## 2.5 — Múltiples Servicios por Turno (Turnos Compuestos)

Permite reservar más de un servicio en la misma cita (ej: corte + tinte).

### Migración
```sql
-- Tabla de relación turno↔servicios
CREATE TABLE turno_servicios (
    turno_id BIGINT NOT NULL REFERENCES turnos(id),
    servicio_id BIGINT NOT NULL REFERENCES servicios(id),
    PRIMARY KEY (turno_id, servicio_id)
);

-- La duración total = suma de duraciones de los servicios seleccionados
-- El precio total = suma de precios
```

### Cambios en TurnoRequestDTO
```java
// Antes:
Long servicioId;

// Después:
List<Long> servicioIds;  // al menos uno, calculamos duración/precio total
```

### Cambios en TurnoService.crear()
```java
int duracionTotal = servicios.stream()
    .mapToInt(Servicio::getDuracionMinutos)
    .sum();
BigDecimal precioTotal = servicios.stream()
    .map(Servicio::getPrecio)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

---

## 2.6 — Turnos Recurrentes

Permite crear un turno que se repite semanalmente.

### Migración
```sql
ALTER TABLE turnos ADD COLUMN turno_padre_id BIGINT REFERENCES turnos(id);
ALTER TABLE turnos ADD COLUMN es_recurrente BOOLEAN DEFAULT FALSE;
ALTER TABLE turnos ADD COLUMN recurrencia_semanas INTEGER;  -- repetir N semanas
```

### TurnoRequestDTO
```java
Boolean recurrente;         // si true, crear múltiples turnos
Integer semanas;            // cuántas semanas repetir (max 52)
```

### Lógica en TurnoService.crear()
```java
if (dto.isRecurrente()) {
    Turno padre = crearTurnoUnico(dto);  // turno base
    for (int i = 1; i <= dto.getSemanas(); i++) {
        TurnoRequestDTO repetido = dto.conFecha(
            dto.getFechaHoraInicio().plusWeeks(i)
        );
        crearTurnoUnico(repetido).setTurnoPadreId(padre.getId());
    }
}
```

---

## 2.7 — Timezone-Aware DateTime

**Problema actual:** `LocalDateTime` no guarda zona horaria. Si el servidor está en UTC y el negocio en GMT-3, los turnos se guardan con hora incorrecta.

### Cambio en Entidad Turno
```java
// Antes
private LocalDateTime fechaHoraInicio;
private LocalDateTime fechaHoraFin;

// Después (y migración de columnas a TIMESTAMPTZ)
private Instant fechaHoraInicio;  // UTC en DB
private Instant fechaHoraFin;     // UTC en DB
```

### Migración
```sql
ALTER TABLE turnos 
    ALTER COLUMN fecha_hora_inicio TYPE TIMESTAMPTZ,
    ALTER COLUMN fecha_hora_fin TYPE TIMESTAMPTZ;
```

### Conversión en TurnoService
```java
ZoneId zonaDelTenant = ZoneId.of(tenant.getTimezone());
ZonedDateTime zonedInicio = dto.getFechaHoraInicio()
    .atZone(zonaDelTenant)
    .withZoneSameInstant(ZoneOffset.UTC);
turno.setFechaHoraInicio(zonedInicio.toInstant());
```

---

## 2.8 — Notificaciones WhatsApp (Opcional, pero valioso en LATAM)

### Opción A: Twilio WhatsApp API
```xml
<dependency>
    <groupId>com.twilio.sdk</groupId>
    <artifactId>twilio</artifactId>
    <version>10.1.1</version>
</dependency>
```

### Opción B: Meta Business API (gratis, más complejo)

### WhatsAppService.java (paralelo a NotificacionService)
```java
@Service
@ConditionalOnProperty(name="whatsapp.enabled", havingValue="true")
public class WhatsAppService {
    
    @Async
    public void enviarConfirmacion(Turno turno) {
        if (turno.getCliente().getTelefono() == null) return;
        
        String mensaje = String.format(
            "Hola %s! Tu turno en %s está confirmado para el %s a las %s. " +
            "Para cancelar: %s",
            turno.getCliente().getNombre(),
            turno.getTenant().getNombre(),
            formatFecha(turno.getFechaHoraInicio()),
            formatHora(turno.getFechaHoraInicio()),
            "https://app.tunegocio.com/cancelar/" + turno.getId()
        );
        // Enviar via Twilio/Meta API
    }
}
```

---

## Checklist FASE 2

- [ ] V8: fechas_bloqueadas + FechaBloqueada entity + FechaBloqueadaRepository
- [ ] FechaBloqueadaService + endpoints en DisponibilidadController
- [ ] Integrar bloqueos en calcularSlots()
- [ ] V9: categorias + categoría_id en servicios
- [ ] CategoriaController + CategoriaService
- [ ] Filtro por categoría en GET /api/servicios
- [ ] Cloudinary dependency + UploadService
- [ ] V10: turno_historial + TurnoHistorialRepository
- [ ] Insertar en historial en cada cambio de estado/fecha
- [ ] GET /api/turnos/{id}/historial
- [ ] turno_servicios (múltiples servicios por turno) — migración + lógica
- [ ] Turnos recurrentes — migración + lógica en TurnoService
- [ ] Timezone: migrar columnas a TIMESTAMPTZ, usar Instant en Java
- [ ] Conversión de timezone en TurnoService con ZoneId del tenant
- [ ] (Opcional) WhatsAppService con Twilio
