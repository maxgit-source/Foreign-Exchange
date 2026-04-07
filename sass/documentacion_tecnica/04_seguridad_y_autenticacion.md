# Seguridad y Autenticación

## Modelo de Seguridad

El sistema usa **JWT stateless** con refresh token rotation. No hay sesiones HTTP. Cada request lleva su propio contexto de autenticación en el header.

---

## Flujo Completo de Autenticación

```
1. REGISTRO / LOGIN
   ─────────────────
   Client ──POST /api/auth/register──▶ AuthService
   
   AuthService:
   a) Valida email único (global para usuarios, por tenant para tenants)
   b) Hashea password con BCrypt (10 rounds)
   c) Crea Tenant + Usuario OWNER en transacción
   d) Genera slug único (ej: "mi peluquería" → "mi-peluqueria-1")
   e) Genera Access Token JWT (1 hora)
   f) Genera Refresh Token (UUID raw → SHA-256 → DB)
   g) Retorna AuthResponseDTO

2. REQUEST AUTENTICADO
   ──────────────────────
   Client ──GET /api/turnos──▶ JwtAuthenticationFilter
   
   Filter:
   a) Extrae "Authorization: Bearer <token>"
   b) Valida firma HMAC-SHA256 con secret
   c) Verifica expiración
   d) Extrae claims: sub (email), tenantId, role, nombre
   e) Carga Usuario desde DB por email
   f) Setea SecurityContextHolder con UsernamePasswordAuthenticationToken
   g) Continúa cadena de filtros

3. REFRESH TOKEN
   ─────────────
   Client ──POST /api/auth/refresh──▶ RefreshTokenService
   
   a) Hashea el raw token recibido con SHA-256
   b) Busca en DB: token_hash AND revoked=false AND expires_at > NOW()
   c) Si válido: revoca el token actual (revoked=true)
   d) Genera nuevo Access Token + nuevo Refresh Token
   e) Retorna AuthResponseDTO (token rotation completo)

4. LOGOUT
   ───────
   Client ──POST /api/auth/logout──▶ RefreshTokenService.revokeAllByUsuarioId()
   UPDATE refresh_tokens SET revoked=true WHERE usuario_id = ?
```

---

## JWT — Detalle de Implementación

### Claims del Access Token
```json
{
  "sub": "juan@miempresa.com",
  "tenantId": "1",
  "role": "OWNER",
  "nombre": "Juan Pérez",
  "iat": 1712300000,
  "exp": 1712303600
}
```

### Configuración
```yaml
jwt:
  secret: ${JWT_SECRET}          # Debe ser string base64 de mínimo 256 bits
  expiration: 3600000            # 1 hora en ms
  refresh-expiration: 604800000  # 7 días en ms
```

### Algoritmo
- **Firma:** HMAC-SHA256 (HS256)
- **Biblioteca:** JJWT 0.11.5

---

## Refresh Token — Seguridad de Diseño

| Aspecto | Implementación |
|---|---|
| Almacenamiento en DB | SHA-256 del token raw (nunca el token real) |
| Transmisión al cliente | Token raw (UUID) en AuthResponseDTO |
| Validación | Hash del raw recibido == token_hash en DB |
| Single-use | Revocado inmediatamente al usarse |
| Expiración | 7 días (configurable) |
| Logout | Revoca TODOS los tokens del usuario (multi-dispositivo) |

---

## Roles y Autorización

### Jerarquía
```
ADMIN > OWNER > STAFF > CLIENT
```

### Matriz de Permisos por Endpoint

| Recurso | ADMIN | OWNER | STAFF | CLIENT | Público |
|---|---|---|---|---|---|
| POST /api/auth/** | — | — | — | — | ✓ |
| GET /api/turnos | ✓ | ✓ | ✓ | — | — |
| POST /api/turnos | ✓ | ✓ | ✓ | — | — |
| PATCH /api/turnos/{id}/* | ✓ | ✓ | ✓ | — | — |
| GET /api/clientes | ✓ | ✓ | ✓ | — | — |
| POST/PUT/DELETE /api/clientes | ✓ | ✓ | ✓ | — | — |
| GET /api/servicios | ✓ | ✓ | ✓ | — | — |
| POST/PUT/DELETE /api/servicios | ✓ | ✓ | — | — | — |
| GET /api/servicios/todos | ✓ | ✓ | — | — | — |
| PUT /api/disponibilidad/{id} | ✓ | ✓ | — | — | — |
| GET /api/disponibilidad/** | ✓ | ✓ | ✓ | — | — |
| GET /api/staff | ✓ | ✓ | ✓ | — | — |
| GET /public/** | — | — | — | — | ✓ |
| POST /public/{slug}/reservar | — | — | — | — | ✓ |

### Implementación en Código
```java
// En el Controller:
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
public ResponseEntity<?> crearTurno(...) { ... }

// En SecurityConfig:
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers("/public/**").permitAll()
.anyRequest().authenticated()
```

---

## CORS

**Configuración actual:**
```yaml
cors:
  allowed-origins: http://localhost:5173,http://localhost:3000
```

**Headers permitidos:**
- Authorization, Content-Type, X-Requested-With, Accept

**Métodos permitidos:**
- GET, POST, PUT, PATCH, DELETE, OPTIONS

**Credentials:** `true` (necesario para enviar Authorization header)

**Faltante para producción:**
- Agregar dominio de producción del frontend en `cors.allowed-origins`
- Configurar via variable de entorno: `CORS_ORIGINS=https://app.tunegocio.com`

---

## Vulnerabilidades Actuales y Mitigaciones

### CRÍTICO — Rate Limiting (NO implementado)
**Riesgo:** Brute force en `/api/auth/login`, abuso de `/public/{slug}/reservar`

**Solución recomendada:** Bucket4j + Redis
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
</dependency>
```

```java
// Ejemplo: 5 intentos de login por IP por minuto
@Configuration
public class RateLimitConfig {
    // 5 req/min para auth
    // 20 req/min para reservas públicas
}
```

### ALTO — JWT Secret en Config
**Riesgo:** El secreto JWT expuesto en código o logs puede comprometer todos los tokens.

**Estado actual:** Usa `${JWT_SECRET}` (correcto — depende de env var)
**Verificar:** Que no haya valor hardcodeado en application.yml o application-dev.yml

**Buena práctica:** JWT secret debe tener mínimo 256 bits (32 bytes) en base64:
```bash
openssl rand -base64 32
```

### MEDIO — Sin Audit Logging
**Riesgo:** Sin trazabilidad de quién modificó qué dato.

**Solución:** Tabla `audit_log` + AOP interceptor en services

### BAJO — No hay 2FA
**Riesgo:** Cuenta comprometida = datos de clientes expuestos.

**Solución futura:** TOTP (Google Authenticator) o email OTP

### BAJO — Sin blacklist de JWT
**Riesgo:** Un JWT robado sigue siendo válido hasta su expiración (1 hora).

**Solución:** Redis con lista de tokens revocados (token JTI en DB/cache)
- Agregar claim `jti` (JWT ID) al token
- En logout/compromiso: agregar JTI a Redis con TTL = expiración del token

---

## Checklist de Seguridad para Producción

- [ ] Rate limiting en /api/auth/login (max 5/min por IP)
- [ ] Rate limiting en /public/{slug}/reservar (max 20/min por IP)
- [ ] HTTPS obligatorio (SSL/TLS en nginx o load balancer)
- [ ] JWT_SECRET como env var, mínimo 256 bits, rotada periódicamente
- [ ] CORS restringido al dominio de producción
- [ ] Audit log de mutaciones (CREATE, UPDATE, DELETE)
- [ ] Blacklist JWT en Redis para logout inmediato
- [ ] Headers de seguridad HTTP (X-Frame-Options, CSP, HSTS)
- [ ] Captcha en reservas públicas (reCAPTCHA v3)
- [ ] Validación de timezone en Tenant (usar lista de ZoneId válidos)
- [ ] Sanitización de `notas` (prevenir XSS si se renderiza en frontend)

---

## Variables de Entorno Requeridas

```env
# Obligatorias
POSTGRES_DB=turnos_db
POSTGRES_USER=tu_usuario
POSTGRES_PASSWORD=tu_password_seguro
DB_URL=jdbc:postgresql://postgres-db:5432/turnos_db
JWT_SECRET=tu-secret-base64-minimo-32-bytes

# Email (Gmail o SMTP propio)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=noreply@tunegocio.com
MAIL_PASSWORD=app-specific-password

# CORS
CORS_ORIGINS=https://app.tunegocio.com,https://tunegocio.com

# Opcionales
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000
```
