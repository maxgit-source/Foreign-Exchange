# Evaluación de Calidad y Deudas Técnicas

## Resumen Ejecutivo

El backend tiene una **base sólida y bien estructurada** para el verticale de turnos/agenda. La arquitectura multi-tenant, la seguridad JWT, y la lógica de disponibilidad están bien implementadas. Sin embargo, hay deudas técnicas críticas que deben resolverse antes de ir a producción con clientes reales.

---

## Calificación por Módulo

| Módulo | Calificación | Estado |
|---|---|---|
| Autenticación JWT + Refresh | 9/10 | Casi perfecto |
| Multi-tenancy (aislamiento) | 9/10 | Consistente en toda la app |
| Gestión de Turnos | 8/10 | Sólido, faltan edge cases |
| Disponibilidad / Slots | 8/10 | Algoritmo correcto |
| Gestión de Clientes | 8/10 | Completo para MVP |
| Gestión de Servicios | 8/10 | Completo para MVP |
| Reserva Pública | 7/10 | Funciona, sin protección anti-abuso |
| Notificaciones Email | 6/10 | Solo confirmación/cancelación, sin scheduler |
| Gestión de Staff | 3/10 | Solo GET, sin CRUD |
| Planes / Billing | 1/10 | Definido pero no implementado |
| Tests | 0/10 | Ausentes completamente |
| Rate Limiting | 0/10 | No implementado |
| Caching | 0/10 | No implementado |
| Observabilidad / Métricas | 1/10 | Solo /actuator/health |
| Analytics / Reportes | 0/10 | No implementado |

---

## Deudas Críticas (Bloquean producción real)

### 1. Sin Tests — CRÍTICO
**Impacto:** Cualquier refactor rompe funcionalidad sin saberlo.
**Archivos afectados:** Todo el proyecto.
**Qué necesita:**
- Unit tests para `TurnoService` (especialmente `crear()` y estado machine)
- Unit tests para `DisponibilidadService.calcularSlots()`
- Unit tests para `AuthService`
- Integration tests para controllers con MockMvc
- Test de solapamiento de turnos con datos límite

**Herramientas:** JUnit 5, Mockito, AssertJ (ya están en pom.xml)

---

### 2. Sin Staff CRUD — CRÍTICO
**Impacto:** Un OWNER no puede agregar profesionales a su negocio desde la API.
**Faltante:**
```
POST   /api/staff           → crear usuario STAFF en el tenant
PUT    /api/staff/{id}      → actualizar nombre, email, habilitado
DELETE /api/staff/{id}      → deshabilitar (enabled=false, no borrar)
POST   /api/staff/invitar   → enviar email de invitación con link de registro
```

---

### 3. Sin Rate Limiting — CRÍTICO para producción
**Riesgo:** Brute force, DoS, spam de reservas.
**Endpoints vulnerables:**
- `/api/auth/login` — brute force de passwords
- `/api/auth/register` — spam de cuentas
- `/public/{slug}/reservar` — spam de turnos

---

### 4. Enforcement de Planes — CRÍTICO para monetización
**Impacto:** Todos los usuarios tienen acceso ilimitado sin importar el plan.
**Lo que falta:**
```java
// En cada service, antes de crear:
planValidator.validarLimite(tenant, "turnos_mensuales", 50); // FREE = 50 turnos/mes
planValidator.validarLimite(tenant, "profesionales", 1);     // FREE = 1 profesional
planValidator.validarLimite(tenant, "servicios", 5);         // FREE = 5 servicios
```

---

## Deudas Altas (Deben resolverse en primeras semanas)

### 5. Sin Audit Log
Un OWNER no sabe quién modificó un turno o borró un cliente.
**Necesita:** Tabla `audit_log` + interceptor AOP en services.

### 6. Sin Recordatorios de Turnos
`NotificacionService.enviarRecordatorio()` existe pero nunca se llama.
**Necesita:** Spring `@Scheduled` o Quartz para enviar recordatorios 24h antes.

### 7. Sin Reprogramación de Turnos
Solo se puede crear o cancelar. No hay `PATCH /api/turnos/{id}/reprogramar`.
**Impacto:** Los clientes deben cancelar y reservar de nuevo.

### 8. Timezone Handling Incompleto
El campo `timezone` en Tenant existe pero los LocalDateTime en turnos no se convierten correctamente cuando el servidor y el negocio están en zonas distintas.
**Necesita:** Usar `ZonedDateTime` o `Instant` en lugar de `LocalDateTime` para `fechaHoraInicio/Fin`.

---

## Deudas Medias

### 9. Sin Paginación en Algunos Endpoints
- `GET /api/staff` → devuelve lista completa (puede ser largo)
- `GET /api/servicios` → sin paginación
- `GET /api/clientes/{id}/historial` → sin paginación (un cliente activo puede tener 1000+ turnos)

### 10. N+1 Queries Potenciales
`TurnoSpecification.conFetchCompleto()` hace LEFT JOIN para clientes, profesionales y servicios. Pero en agenda diaria (`agendaDelDia()`), si no se usa la specification, puede haber N+1.
**Verificar:** Habilitar SQL logging en dev y revisar queries.

### 11. Sin Validación de Superposición en Disponibilidad
Al hacer `PUT /api/disponibilidad/{profesionalId}`, se valida que los bloques no se superpongan entre sí. Pero el servicio devuelve 500 si la validación falla en vez de 422.

### 12. Sin Categorías de Servicios
No hay forma de agrupar servicios por tipo (corte, tinte, manicura).

---

## Deudas Bajas (Post-MVP)

| Deuda | Impacto |
|---|---|
| Sin soporte multi-idioma (i18n) | Solo español |
| Sin exportación de datos (CSV/Excel) | No hay reportes descargables |
| Sin imagen en servicios | No se puede mostrar foto del servicio |
| Sin foto de perfil en profesionales | Widget sin avatares |
| Sin histórico de cambios de plan | No se sabe si el tenant hizo upgrade/downgrade |
| Sin webhooks | No puede integrarse con Zapier/Make |
| JavaDoc ausente en la mayoría de métodos | Dificulta onboarding de nuevos devs |

---

## Lo Que Está Muy Bien (No tocar sin razón)

### Arquitectura de Repositorios
Los queries en `TurnoRepository` están muy bien escritos. El uso de `@Query` con JPQL para el solapamiento es correcto y eficiente:
```java
@Query("""
    SELECT COUNT(t) > 0 FROM Turno t
    WHERE t.profesional.id = :profId
    AND t.estado != 'CANCELADO'
    AND t.fechaHoraInicio < :fin
    AND t.fechaHoraFin > :inicio
""")
boolean existsSolapamiento(...)
```

### Manejo de Refresh Tokens
SHA-256 del raw token, single-use, revocación total en logout. Correcto.

### GlobalExceptionHandler
Cubre todos los casos relevantes con respuestas JSON uniformes. Bien estructurado.

### Algoritmo de Slots
`DisponibilidadService.calcularSlots()` es correcto y considera:
- Múltiples bloques de disponibilidad por día
- Turnos ya reservados como "ocupados"
- Slots pasados si la fecha es hoy

### Slug Generation
`AuthService.generateUniqueSlug()` elimina acentos, slugifica y agrega sufijo numérico si hay conflicto. Robusto.

---

## Métricas de Código Actuales

| Métrica | Valor |
|---|---|
| Líneas de código (estimado) | ~3.500 líneas |
| Archivos Java | ~65 |
| Endpoints REST | 28 implementados |
| Migraciones DB | 5 |
| Cobertura de tests | 0% |
| Dependencias directas | 11 |
| Tiempo de startup estimado | ~8-12 segundos |

---

## Roadmap de Resolución Priorizado

```
SEMANA 1 — Fundamentos (críticos)
├── Staff CRUD completo
├── Rate limiting básico (Bucket4j)
├── Tests unitarios de TurnoService y DisponibilidadService
└── Tests de controllers (auth, turnos)

SEMANA 2 — Calidad
├── Audit log (AOP)
├── Scheduler de recordatorios (Spring @Scheduled)
├── Reprogramación de turnos
├── Paginación en historial de clientes
└── Validación de timezone

SEMANA 3+ — Crecimiento
├── Enforcement de planes
├── Integración de pagos
├── Categorías de servicios
└── Analytics básico
```

Ver documentos en `/plan_de_implementacion/` para el detalle completo por fases.
