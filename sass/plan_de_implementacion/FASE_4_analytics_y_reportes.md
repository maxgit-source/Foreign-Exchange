# FASE 4 — Analytics, Reportes y Observabilidad
**Objetivo:** Dar a los dueños de negocio inteligencia sobre su operación. Habilitar monitoreo de la plataforma.
**Cuando ejecutar:** Con FASE 3 completa o en paralelo a ella.

---

## 4.1 — Dashboard de KPIs (Endpoint de Reportes)

### ReportesController.java
```java
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
@RequiredArgsConstructor
public class ReportesController {

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> dashboard(
        @RequestParam(defaultValue = "30") int dias,
        @AuthenticationPrincipal Usuario usuario
    ) { ... }
    
    @GetMapping("/ingresos")
    public ResponseEntity<IngresoReporteDTO> ingresos(
        @RequestParam LocalDate desde,
        @RequestParam LocalDate hasta,
        @AuthenticationPrincipal Usuario usuario
    ) { ... }
    
    @GetMapping("/turnos/estadisticas")
    public ResponseEntity<TurnoEstadisticasDTO> estadisticasTurnos(...) { ... }
    
    @GetMapping("/clientes/top")
    public ResponseEntity<List<ClienteKpiDTO>> topClientes(
        @RequestParam(defaultValue = "10") int limit,
        @AuthenticationPrincipal Usuario usuario
    ) { ... }
    
    @GetMapping("/servicios/popularidad")
    public ResponseEntity<List<ServicioKpiDTO>> popularidadServicios(...) { ... }
    
    @GetMapping("/exportar/turnos")
    public ResponseEntity<byte[]> exportarTurnos(
        @RequestParam LocalDate desde,
        @RequestParam LocalDate hasta,
        @RequestParam(defaultValue = "CSV") FormatoExporte formato,
        @AuthenticationPrincipal Usuario usuario
    ) { ... }
}
```

### DashboardDTO
```java
public record DashboardDTO(
    // Turnos
    long turnosHoy,
    long turnosSemana,
    long turnosMes,
    double tasaOcupacion,       // % de slots llenos vs disponibles
    double tasaNoShow,          // % de NO_SHOW
    double tasaCancelacion,     // % de CANCELADO
    
    // Ingresos (si tiene planes con pagos)
    BigDecimal ingresosMes,
    BigDecimal ingresosAnterior,
    double variacionIngresos,   // % vs mes anterior
    
    // Clientes
    long clientesNuevos,        // último período
    long clientesTotales,
    
    // Servicios más populares (top 5)
    List<ServicioKpiDTO> serviciosPopulares,
    
    // Horas más ocupadas
    List<HoraOcupacionDTO> horasPico,
    
    // Próximos turnos (hoy)
    List<TurnoResponseDTO> proximosTurnos
) {}
```

### Queries de reportes en TurnoRepository
```java
// Turnos por estado en período
@Query("""
    SELECT t.estado, COUNT(t) FROM Turno t
    WHERE t.tenant.id = :tenantId
    AND t.fechaHoraInicio BETWEEN :desde AND :hasta
    GROUP BY t.estado
""")
List<Object[]> countPorEstado(Long tenantId, LocalDateTime desde, LocalDateTime hasta);

// Ingresos por día
@Query("""
    SELECT CAST(t.fechaHoraInicio AS date), SUM(s.precio)
    FROM Turno t JOIN t.servicio s
    WHERE t.tenant.id = :tenantId
    AND t.estado = 'COMPLETADO'
    AND t.fechaHoraInicio BETWEEN :desde AND :hasta
    GROUP BY CAST(t.fechaHoraInicio AS date)
    ORDER BY 1
""")
List<Object[]> ingresosPorDia(Long tenantId, LocalDateTime desde, LocalDateTime hasta);

// Top clientes por cantidad de turnos
@Query("""
    SELECT c, COUNT(t) as total FROM Turno t JOIN t.cliente c
    WHERE t.tenant.id = :tenantId
    AND t.estado IN ('COMPLETADO', 'CONFIRMADO')
    GROUP BY c ORDER BY total DESC
""")
List<Object[]> topClientes(Long tenantId, Pageable pageable);

// Horas pico
@Query(nativeQuery = true, value = """
    SELECT EXTRACT(HOUR FROM fecha_hora_inicio) as hora, COUNT(*) as cantidad
    FROM turnos
    WHERE tenant_id = :tenantId
    AND estado != 'CANCELADO'
    AND fecha_hora_inicio >= :desde
    GROUP BY hora ORDER BY cantidad DESC
""")
List<Object[]> horasPico(Long tenantId, LocalDateTime desde);
```

---

## 4.2 — Exportación de Datos

### ExportService.java
```java
@Service
public class ExportService {
    
    public byte[] exportarTurnosCSV(List<TurnoResponseDTO> turnos) {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Fecha,Hora,Cliente,Email,Profesional,Servicio,Precio,Estado\n");
        
        for (TurnoResponseDTO t : turnos) {
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%.2f,%s\n",
                t.id(), t.fechaHoraInicio().toLocalDate(), 
                t.fechaHoraInicio().toLocalTime(),
                t.nombreCliente(), t.emailCliente(),
                t.nombreProfesional(), t.nombreServicio(),
                t.precioServicio(), t.estado()
            ));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    public byte[] exportarTurnosExcel(List<TurnoResponseDTO> turnos) {
        // Apache POI o simple XLSX
    }
    
    public byte[] exportarClientesPDF(List<ClienteResponseDTO> clientes) {
        // iText o Apache PDFBox
    }
}
```

### Dependencias para exportación
```xml
<!-- Apache POI para Excel -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- iText para PDF -->
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext7-core</artifactId>
    <version>8.0.5</version>
    <type>pom</type>
</dependency>
```

---

## 4.3 — Observabilidad de la Plataforma

### Métricas con Micrometer + Prometheus

**Dependencias:**
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**application.yml:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**MetricasService.java — métricas custom:**
```java
@Service
@RequiredArgsConstructor
public class MetricasService {
    
    private final MeterRegistry registry;
    
    public void registrarTurnoCreado(String tenantSlug, String plan) {
        registry.counter("tunegocio.turnos.creados",
            "tenant", tenantSlug,
            "plan", plan
        ).increment();
    }
    
    public void registrarLogin(boolean exitoso) {
        registry.counter("tunegocio.auth.logins",
            "resultado", exitoso ? "exitoso" : "fallido"
        ).increment();
    }
    
    public Timer.Sample iniciarTimerRequest(String endpoint) {
        return Timer.start(registry);
    }
}
```

### Stack de Observabilidad (Docker Compose extendido)
```yaml
# docker-compose.monitoring.yml
services:
  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports: ["3001:3000"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin123
```

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'tunegocio-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api:8080']
```

---

## 4.4 — Logging Estructurado

### Cambiar a Logback con formato JSON (para ELK/Datadog)

**logback-spring.xml:**
```xml
<configuration>
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"app":"tunegocio-api","env":"${SPRING_PROFILES_ACTIVE}"}</customFields>
    </encoder>
  </appender>
  
  <root level="INFO">
    <appender-ref ref="JSON"/>
  </root>
</configuration>
```

**Dependencia:**
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**Logging contextual en services:**
```java
// En cada service, agregar MDC para tenant tracking
MDC.put("tenantId", usuario.getTenant().getId().toString());
MDC.put("usuarioId", usuario.getId().toString());
log.info("Turno creado: turnoId={}, profesionalId={}", turno.getId(), turno.getProfesional().getId());
MDC.clear();
```

---

## 4.5 — Notificaciones Log (Trazabilidad de Emails)

### Migración V15__create_notificaciones_log.sql
```sql
CREATE TABLE notificaciones_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    tipo VARCHAR(50) NOT NULL,          -- CONFIRMACION, CANCELACION, RECORDATORIO, INVITACION
    destinatario_email VARCHAR(200) NOT NULL,
    asunto VARCHAR(500),
    estado VARCHAR(20) NOT NULL,        -- ENVIADO, ERROR
    error_detalle TEXT,                 -- NULL si fue exitoso
    turno_id BIGINT REFERENCES turnos(id),
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Actualizar NotificacionService:**
```java
@Async
public void enviarConfirmacionTurno(Turno turno) {
    try {
        // ... envío del email ...
        notificacionesLogRepo.save(new NotificacionLog(
            "CONFIRMACION", turno.getCliente().getEmail(), 
            "ENVIADO", null, turno
        ));
    } catch (Exception e) {
        log.error("Error enviando email de confirmación para turno {}", turno.getId(), e);
        notificacionesLogRepo.save(new NotificacionLog(
            "CONFIRMACION", turno.getCliente().getEmail(), 
            "ERROR", e.getMessage(), turno
        ));
    }
}
```

---

## 4.6 — Plan de Limits Dashboard (para ADMIN de plataforma)

### Endpoints /api/admin/
```
GET /api/admin/metricas              → KPIs de la plataforma completa [ADMIN]
GET /api/admin/tenants               → todos los tenants con plan y uso [ADMIN]
GET /api/admin/tenants/{id}/uso      → detalle de uso de un tenant [ADMIN]
POST /api/admin/tenants/{id}/plan    → cambiar plan manualmente [ADMIN]
```

### PlataformaMetricasDTO
```java
public record PlataformaMetricasDTO(
    long totalTenants,
    long tenantsActivos,      // al menos 1 turno último mes
    Map<String, Long> tenantsPorPlan,    // { FREE: 150, BASIC: 30, PRO: 10 }
    long totalTurnos,
    long turnosHoy,
    long totalClientes,
    long totalUsuarios
) {}
```

---

## Checklist FASE 4

- [ ] ReportesController con todos los endpoints de KPIs
- [ ] DashboardDTO, IngresoReporteDTO, TurnoEstadisticasDTO
- [ ] Queries de reporte en TurnoRepository (countPorEstado, ingresosPorDia, etc.)
- [ ] TopClientes, popularidadServicios, horasPico queries
- [ ] PlanValidator.validarPuedeVerReportes() en ReportesController
- [ ] ExportService (CSV, Excel)
- [ ] Apache POI dependency
- [ ] Endpoint GET /api/reportes/exportar/turnos (CSV + Excel)
- [ ] Micrometer + Prometheus dependency
- [ ] Exposición de /actuator/prometheus
- [ ] MetricasService con counters custom
- [ ] docker-compose.monitoring.yml (Prometheus + Grafana)
- [ ] logback-spring.xml con JSON encoder
- [ ] MDC.put() en services para contexto de tenant
- [ ] V15: notificaciones_log
- [ ] Actualizar NotificacionService con log de éxito/error
- [ ] PlataformaMetricasDTO + endpoints /api/admin/metricas
- [ ] Tests de ReportesController
- [ ] Tests de ExportService (verificar CSV/Excel correctos)
