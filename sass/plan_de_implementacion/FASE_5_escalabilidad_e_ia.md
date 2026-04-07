# FASE 5 — Escalabilidad, Producción e IA
**Objetivo:** Preparar la plataforma para escalar a miles de tenants, agregar capacidades de IA y llevarla a producción robusta.
**Cuando ejecutar:** Con las fases anteriores completas. Esta es la visión de largo plazo.

---

## 5.1 — Caching con Redis

### Dependencias
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### CacheConfig.java
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );
        
        Map<String, RedisCacheConfiguration> caches = new HashMap<>();
        caches.put("servicios", config.entryTtl(Duration.ofHours(1)));     // cambian poco
        caches.put("staff", config.entryTtl(Duration.ofHours(1)));
        caches.put("disponibilidad", config.entryTtl(Duration.ofMinutes(5)));
        caches.put("slots", config.entryTtl(Duration.ofMinutes(2)));       // cambian rápido
        caches.put("tenant-publico", config.entryTtl(Duration.ofHours(6)));
        
        return RedisCacheManager.builder(factory)
            .withInitialCacheConfigurations(caches)
            .build();
    }
}
```

### Aplicar Cache en Services
```java
// ServicioService.java
@Cacheable(value = "servicios", key = "#tenantId")
public List<ServicioResponseDTO> listarActivos(Long tenantId) { ... }

@CacheEvict(value = "servicios", key = "#tenantId")
public ServicioResponseDTO crear(ServicioRequestDTO dto, Usuario usuario) { ... }

// DisponibilidadService.java
@Cacheable(value = "slots", key = "#profesionalId + ':' + #fecha + ':' + #servicioId")
public List<SlotDisponibleDTO> calcularSlots(...) { ... }

@CacheEvict(value = {"slots", "disponibilidad"}, allEntries = true)
public List<DisponibilidadResponseDTO> reemplazar(...) { ... }
```

### Redis en Docker Compose
```yaml
redis:
  image: redis:7-alpine
  ports: ["6379:6379"]
  command: redis-server --requirepass ${REDIS_PASSWORD}
  volumes:
    - redis_data:/data
```

---

## 5.2 — Migración a UUIDs (Opcional pero recomendado para distribución)

Actualmente usa `BIGSERIAL` (autoincremental). Para microservicios o multi-región, UUID es mejor:

### Por qué migrar
- Los IDs autoincrementales exponen volumen de negocio (cliente puede inferir cuántos turnos hay)
- UUID permite crear IDs en el cliente sin consultar la DB
- Necesario para eventual event sourcing / CQRS

### Cómo migrar (sin downtime)
1. Agregar columna `uuid UUID DEFAULT gen_random_uuid()` a las tablas
2. Migrar código Java a usar UUID como PK
3. Deprecar BIGSERIAL (mantener como surrogate key interno)

```java
// En entidades:
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

---

## 5.3 — Message Queue para Operaciones Asíncronas (RabbitMQ / Kafka)

### Cuándo usarlo
Cuando el volumen de emails, webhooks y notificaciones justifique colas dedicadas.

### Para emails con RabbitMQ
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

```java
// RabbitMQConfig.java
@Bean Queue emailQueue() { return new Queue("tunegocio.emails", true); }

// Publisher (reemplaza @Async en NotificacionService):
rabbitTemplate.convertAndSend("tunegocio.emails", new EmailEvent(
    turno.getCliente().getEmail(),
    "CONFIRMACION",
    turno.getId()
));

// Consumer (en worker separado):
@RabbitListener(queues = "tunegocio.emails")
public void procesarEmail(EmailEvent event) { /* enviar email real */ }
```

### Para eventos de negocio con Kafka (escala mayor)
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Eventos a publicar:
- `turno.creado` → triggers: email confirmación, descuento stock, KPIs
- `turno.cancelado` → triggers: email cancelación, liberar slot
- `pago.confirmado` → triggers: email recibo, activar suscripción
- `tenant.registrado` → triggers: email bienvenida, configuración inicial

---

## 5.4 — Capacidades de IA

### 5.4.1 — Scheduling Inteligente con IA

**Problema:** El algoritmo actual de slots es simple (disponibilidad horaria - turnos existentes). Con IA, podemos predecir qué slot aceptará el cliente basado en historial.

**Implementación con Claude API:**
```java
@Service
@ConditionalOnProperty("ai.enabled")
public class AISchedulingService {
    
    @Value("${anthropic.api-key}")
    private String apiKey;
    
    public List<SlotRecomendadoDTO> recomendarSlots(Long clienteId, Long servicioId) {
        // 1. Cargar historial del cliente (horarios preferidos, profesional preferido)
        List<Turno> historial = turnoRepo.findByClienteId(clienteId, Pageable.ofSize(20));
        
        // 2. Obtener slots disponibles
        List<SlotDisponibleDTO> slotsLibres = calcularTodosLosSlots(servicioId, 7);
        
        // 3. Llamar a Claude API con contexto
        String prompt = buildPrompt(historial, slotsLibres);
        String respuesta = callClaudeAPI(prompt);
        
        // 4. Parsear respuesta y rankear slots
        return parsearRecomendaciones(respuesta, slotsLibres);
    }
    
    private String buildPrompt(List<Turno> historial, List<SlotDisponibleDTO> slots) {
        return """
            Eres un asistente de scheduling para un negocio de servicios.
            
            Historial del cliente:
            %s
            
            Slots disponibles esta semana:
            %s
            
            Basándote en las preferencias del cliente (hora del día, día de la semana, profesional),
            recomienda los 3 mejores slots. Responde SOLO con JSON: [{"slot": "...", "razon": "..."}]
        """.formatted(formatearHistorial(historial), formatearSlots(slots));
    }
}
```

### 5.4.2 — Asistente de Atención al Cliente (Chatbot)

**Widget de chat en el booking público:**
```java
@PostMapping("/public/{slug}/chat")
public ResponseEntity<ChatResponseDTO> chat(
    @PathVariable String slug,
    @RequestBody ChatRequestDTO request
) {
    // El chatbot puede:
    // - Responder preguntas sobre servicios y precios
    // - Ayudar a elegir profesional y horario
    // - Verificar disponibilidad
    // - Crear reservas
}
```

**Flujo con Claude:**
```java
@Service
public class ChatbotService {
    
    public String responder(String mensaje, String tenantSlug, List<Message> historial) {
        Tenant tenant = tenantRepo.findBySlug(tenantSlug).orElseThrow();
        List<Servicio> servicios = servicioRepo.findActivos(tenant.getId());
        
        String systemPrompt = """
            Eres el asistente virtual de %s.
            Servicios disponibles: %s
            
            Puedes ayudar a los clientes a:
            1. Informarse sobre servicios y precios
            2. Verificar disponibilidad (llama a check_availability)
            3. Hacer una reserva (llama a create_booking)
            
            Responde siempre en el idioma del cliente. Sé amigable y conciso.
        """.formatted(tenant.getNombre(), formatServicios(servicios));
        
        // Tool use con Claude API para actions (check_availability, create_booking)
        return callClaudeWithTools(systemPrompt, historial, mensaje);
    }
}
```

### 5.4.3 — Análisis de Sentiment en Notas de Turno

```java
@Service
public class SentimentService {
    
    public void analizarFeedback(Turno turno, String feedbackCliente) {
        if (feedbackCliente == null || feedbackCliente.isBlank()) return;
        
        // Llamar a Claude con el feedback
        String resultado = callClaude("""
            Analiza el siguiente feedback de un cliente y clasifícalo.
            Feedback: "%s"
            Responde SOLO con JSON: {"sentimiento": "POSITIVO|NEGATIVO|NEUTRO", "urgente": true/false}
        """.formatted(feedbackCliente));
        
        SentimentResult sentiment = parseJson(resultado);
        if (sentiment.urgente()) {
            // Alertar al OWNER por email/WhatsApp
            notificacionService.alertarOwner(turno.getTenant(), 
                "Feedback urgente del cliente: " + feedbackCliente);
        }
    }
}
```

### 5.4.4 — Predicción de No-Show

```java
@Service
public class NoShowPredictor {
    
    // Basado en historial del cliente y del día/hora
    public double predecirProbabilidadNoShow(Turno turno) {
        // Factores:
        // - Histórico de no-shows del cliente
        // - Día de la semana (lunes AM tienen más no-shows)
        // - Tiempo de anticipación de la reserva
        // - Si tiene recordatorio enviado
        
        // Con ML simple (sin IA externa): regresión logística
        // Con Claude: enviar los factores y pedir predicción
        
        // Si probabilidad > 60%, enviar recordatorio extra
    }
}
```

---

## 5.5 — Multi-Región y Alta Disponibilidad

### Arquitectura de producción recomendada

```
Internet
    │
    ▼
CloudFlare (CDN + DDoS protection)
    │
    ▼
Load Balancer (AWS ALB / GCP LB)
    │
    ├──── Spring Boot API (instancia 1)
    ├──── Spring Boot API (instancia 2)  ← Horizontal scaling
    └──── Spring Boot API (instancia N)
              │
              ├──── PostgreSQL (RDS Multi-AZ)
              ├──── Redis (ElastiCache)
              └──── S3 (imágenes)
```

### Spring Session con Redis (para stateless scaling)
Ya implementado (JWT stateless). No requiere cambios.

### Variables de entorno para producción
```env
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://rds-endpoint:5432/turnos_db
REDIS_URL=redis://elasticache-endpoint:6379
JWT_SECRET=<generado con openssl rand -base64 64>
CLOUDINARY_URL=cloudinary://...
MERCADOPAGO_ACCESS_TOKEN=APP_USR-...
STRIPE_SECRET_KEY=sk_live_...
ANTHROPIC_API_KEY=sk-ant-...
```

---

## 5.6 — API Pública para Integraciones (Webhooks + API Keys)

### Para que terceros (Zapier, sitios propios, etc.) se integren

**Migración V16__create_api_keys.sql:**
```sql
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    nombre VARCHAR(100) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 de la API key
    permisos TEXT[],                         -- ['read:turnos', 'write:turnos']
    activo BOOLEAN DEFAULT TRUE,
    ultimo_uso TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE webhooks (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    url TEXT NOT NULL,
    eventos TEXT[] NOT NULL,    -- ['turno.creado', 'turno.cancelado', 'pago.confirmado']
    secret_hash VARCHAR(64),    -- para firmar payloads
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Endpoints:**
```
POST   /api/integraciones/api-keys          → generar API key [OWNER, plan PRO+]
GET    /api/integraciones/api-keys          → listar API keys
DELETE /api/integraciones/api-keys/{id}     → revocar API key
POST   /api/integraciones/webhooks          → registrar webhook endpoint
GET    /api/integraciones/webhooks          → listar webhooks
DELETE /api/integraciones/webhooks/{id}     → eliminar webhook
```

**Autenticación por API Key (alternativa a JWT):**
```java
// ApiKeyAuthFilter.java
// Header: X-API-Key: tunegocio_xxxxxxxxxxxxx
// Busca por hash, verifica permisos, inyecta tenant context
```

---

## 5.7 — Internacionalización (i18n)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

```java
@Configuration
public class I18nConfig {
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ...;
        source.setBasenames("classpath:messages");
        source.setDefaultLocale(new Locale("es", "AR"));
        return source;
    }
}
```

```properties
# messages_es.properties
turno.solapamiento=Ya existe un turno en ese horario para el profesional seleccionado
turno.fecha.pasada=No se pueden crear turnos en fechas pasadas
cliente.email.duplicado=Ya existe un cliente con ese email en este negocio

# messages_en.properties
turno.solapamiento=There is already an appointment at that time for the selected professional
turno.fecha.pasada=Cannot create appointments in the past
```

---

## 5.8 — Evolución del Modelo Multi-Tenant

Con schema-per-tenant ya implementado desde FASE 1, en esta fase se agrega soporte para el modelo híbrido por plan:

### DB dedicada para tenants Enterprise

```java
// DataSourceRouter — decide qué DataSource usar según el tenant
@Component
public class TenantDataSourceRouter extends AbstractRoutingDataSource {
    
    // Map de tenants Enterprise con su propia DB
    private final Map<String, DataSource> enterpriseDataSources = new ConcurrentHashMap<>();
    
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenant();
    }
    
    // Registrar una DB nueva para un tenant Enterprise al contratar
    public void registrarDataSourceEnterprise(String tenantSlug, DataSource ds) {
        enterpriseDataSources.put("tenant_" + tenantSlug, ds);
        this.afterPropertiesSet();  // refresh del router
    }
}
```

### Flujo de onboarding por plan

```
Nuevo tenant FREE/BASIC/PRO
  → SchemaService.crearSchema() en DB compartida
  → Flyway migra las tablas en el nuevo schema
  → Listo en segundos

Nuevo tenant ENTERPRISE
  → Provisionar nueva instancia RDS (via AWS SDK o Terraform)
  → SchemaService.crearSchema() en esa DB dedicada
  → DataSourceRouter.registrarDataSourceEnterprise()
  → Listo (puede tardar minutos, proceso asíncrono)
```

### Stack de Bases de Datos por Etapa

| Etapa | Qué agregar | Para qué |
|---|---|---|
| FASE 1 (ya hecho) | PostgreSQL schema-per-tenant | Aislamiento de datos por negocio |
| FASE 1 | Redis | Caché + rate limiting + sesiones |
| FASE 2 | Cloudinary/S3 | Imágenes de servicios y staff |
| FASE 3 | Redis Streams | Cola de eventos (pagos, webhooks) |
| FASE 4 | TimescaleDB o ClickHouse | Analytics de series temporales |
| FASE 5 | Elasticsearch | Búsqueda full-text de productos |
| FASE 5 | pgvector (extensión PostgreSQL) | Embeddings para búsqueda semántica con IA |
| FASE 5 | DB dedicada por tenant Enterprise | Aislamiento total, compliance |

---

## Checklist FASE 5

- [ ] Redis dependency + CacheConfig.java
- [ ] @Cacheable en ServicioService, DisponibilidadService, PublicBookingController
- [ ] @CacheEvict en todos los métodos que mutan datos cacheados
- [ ] Redis en docker-compose.yml
- [ ] RabbitMQ o Kafka para emails en alta carga
- [ ] Evaluar migración a UUID (si se planea microservicios)
- [ ] AISchedulingService con Claude API (experimental)
- [ ] ChatbotService para widget de booking (con tool use)
- [ ] SentimentService para análisis de feedback
- [ ] NoShowPredictor (ML o Claude)
- [ ] V16: api_keys + webhooks
- [ ] ApiKeyAuthFilter (autenticación alternativa a JWT)
- [ ] WebhookDispatcher (publicar eventos a URLs registradas)
- [ ] docker-compose.monitoring.yml (Prometheus + Grafana)
- [ ] i18n: MessageSource + messages_es.properties + messages_en.properties
- [ ] Configuración de producción: RDS, ElastiCache, S3, CloudFlare
- [ ] Health checks avanzados en /actuator/health (DB, Redis, Mail)
- [ ] Tests de carga (JMeter o k6) antes de producción
