# Stack Frontend y Conexión con el Backend

## Recomendación Principal: React + TypeScript + Next.js

Dado que el backend es una API REST con JWT y ya tiene CORS configurado para `localhost:3000` y `localhost:5173`, el stack frontend más compatible y productivo es:

---

## Stack Recomendado (Prioridad Alta)

### Framework: Next.js 14+ (App Router)
**Por qué Next.js sobre React puro:**
- SSR/SSG para páginas públicas (landing, widget de booking público → SEO)
- App Router permite layouts compartidos por tenant
- API Routes para proxying seguro de tokens (opcional)
- Image optimization integrada (logos de tenants)
- File-based routing simplifica la estructura multi-tenant

```
Alternativa válida: React + Vite (si no necesitas SSR)
→ El backend ya tiene CORS para localhost:5173 (Vite default)
```

### Lenguaje: TypeScript
Obligatorio. El backend tiene DTOs bien definidos → generar tipos automáticamente.

### Fetching: TanStack Query (React Query) v5
```bash
npm i @tanstack/react-query
```
- Cache automático por query key
- Invalidación inteligente al mutar
- Estados loading/error/success
- Retry automático con backoff
- Integración perfecta con JWT (interceptores en Axios)

### HTTP Client: Axios + Interceptores JWT
```typescript
// src/lib/api.ts
const api = axios.create({ baseURL: process.env.NEXT_PUBLIC_API_URL });

// Adjunta accessToken automáticamente
api.interceptors.request.use(config => {
  const token = getAccessToken(); // desde localStorage o cookie
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Refresh automático en 401
api.interceptors.response.use(
  res => res,
  async error => {
    if (error.response?.status === 401) {
      const newToken = await refreshAccessToken();
      error.config.headers.Authorization = `Bearer ${newToken}`;
      return api(error.config);
    }
    return Promise.reject(error);
  }
);
```

### Estado Global: Zustand
```bash
npm i zustand
```
- Para auth state (usuario, tenantId, role, token)
- Sin boilerplate de Redux
- Persist middleware para localStorage

```typescript
// src/store/auth.ts
interface AuthState {
  user: AuthResponseDTO | null;
  accessToken: string | null;
  setAuth: (data: AuthResponseDTO) => void;
  logout: () => void;
}
```

### UI Components: shadcn/ui + Tailwind CSS
- shadcn/ui: componentes accesibles, sin opinionation
- Tailwind: styling rápido y responsivo
- Compatible con el `colorPrimario` del tenant (CSS variables dinámicas)

### Formularios: React Hook Form + Zod
```bash
npm i react-hook-form zod @hookform/resolvers
```
- Zod para validación en cliente (mismas reglas que Jakarta Validation del backend)
- Reduce errores de UX por validación tardía

### Calendario / Agenda: FullCalendar o react-big-calendar
Para la vista de agenda de turnos (día, semana, mes).

---

## Estructura de Proyecto Frontend Recomendada

```
frontend/
├── src/
│   ├── app/                          ← Next.js App Router
│   │   ├── (auth)/
│   │   │   ├── login/page.tsx
│   │   │   └── registro/page.tsx
│   │   ├── (dashboard)/              ← Layout con sidebar
│   │   │   ├── layout.tsx
│   │   │   ├── agenda/page.tsx       ← Vista calendario
│   │   │   ├── turnos/page.tsx       ← Lista de turnos
│   │   │   ├── clientes/page.tsx
│   │   │   ├── servicios/page.tsx
│   │   │   ├── staff/page.tsx
│   │   │   └── configuracion/page.tsx
│   │   └── booking/[slug]/           ← Widget público de reservas
│   │       └── page.tsx              ← SSR con /public/{slug}
│   │
│   ├── lib/
│   │   ├── api.ts                    ← Axios instance + interceptores
│   │   └── queryClient.ts            ← TanStack Query client config
│   │
│   ├── hooks/                        ← Custom hooks por módulo
│   │   ├── useTurnos.ts
│   │   ├── useClientes.ts
│   │   ├── useServicios.ts
│   │   └── useDisponibilidad.ts
│   │
│   ├── store/
│   │   └── auth.ts                   ← Zustand auth store
│   │
│   ├── types/                        ← Tipos TypeScript de los DTOs
│   │   ├── auth.ts
│   │   ├── turno.ts
│   │   ├── cliente.ts
│   │   └── servicio.ts
│   │
│   └── components/
│       ├── ui/                       ← shadcn/ui components
│       ├── turnos/
│       │   ├── TurnoCard.tsx
│       │   ├── TurnoForm.tsx
│       │   └── TurnoCalendario.tsx
│       ├── clientes/
│       └── booking/                  ← Widget de reserva pública
│           ├── BookingWizard.tsx
│           ├── StepServicio.tsx
│           ├── StepProfesional.tsx
│           ├── StepSlot.tsx
│           └── StepDatos.tsx
```

---

## Tipos TypeScript desde DTOs del Backend

Generar los tipos automáticamente desde el OpenAPI JSON:
```bash
npm i -D @openapitools/openapi-generator-cli
npx openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g typescript-axios \
  -o src/api-client
```

O definirlos manualmente (más control):

```typescript
// src/types/auth.ts
export interface AuthResponseDTO {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  usuarioId: number;
  nombre: string;
  email: string;
  role: 'ADMIN' | 'OWNER' | 'STAFF' | 'CLIENT';
  tenantId: number;
  tenantNombre: string;
  tenantSlug: string;
}

// src/types/turno.ts
export interface TurnoResponseDTO {
  id: number;
  clienteId: number;
  nombreCliente: string;
  emailCliente: string;
  telefonoCliente?: string;
  profesionalId: number;
  nombreProfesional: string;
  servicioId: number;
  nombreServicio: string;
  duracionMinutos: number;
  precioServicio: number;
  fechaHoraInicio: string; // ISO DateTime
  fechaHoraFin: string;
  estado: 'PENDIENTE' | 'CONFIRMADO' | 'COMPLETADO' | 'CANCELADO' | 'NO_SHOW';
  notas?: string;
  createdAt: string;
}

export interface TurnoRequestDTO {
  clienteId: number;
  profesionalId: number;
  servicioId: number;
  fechaHoraInicio: string;
  notas?: string;
}
```

---

## Conexión de Autenticación

```typescript
// src/hooks/useAuth.ts
import { useAuthStore } from '@/store/auth';
import { api } from '@/lib/api';

export function useLogin() {
  const setAuth = useAuthStore(s => s.setAuth);
  
  return useMutation({
    mutationFn: (data: { email: string; password: string }) =>
      api.post<AuthResponseDTO>('/api/auth/login', data).then(r => r.data),
    onSuccess: (data) => {
      setAuth(data);
      router.push('/agenda');
    }
  });
}

export function useLogout() {
  const logout = useAuthStore(s => s.logout);
  
  return useMutation({
    mutationFn: () => api.post('/api/auth/logout'),
    onSuccess: () => {
      logout();
      router.push('/login');
    }
  });
}
```

---

## Widget de Reserva Pública (Embebible)

El endpoint `/public/{slug}` permite construir un widget embebible en cualquier sitio:

```typescript
// src/components/booking/BookingWizard.tsx
// Flujo de 4 pasos:
// 1. Elegir servicio (GET /public/{slug}/servicios)
// 2. Elegir profesional (GET /public/{slug}/profesionales)
// 3. Elegir slot (GET /public/{slug}/slots?profesionalId=X&servicioId=Y&fecha=Z)
// 4. Ingresar datos + confirmar (POST /public/{slug}/reservar)
```

Este widget puede ofrecerse como iframe embebible:
```html
<!-- Código para que el cliente ponga en su web -->
<iframe src="https://app.tunegocio.com/booking/mi-peluqueria" />
```

---

## Otras Tecnologías por Vertical Futuro

### Para E-commerce
| Necesidad | Tecnología |
|---|---|
| Pagos | Mercado Pago SDK (LATAM) + Stripe (global) |
| Carrito de compras | Estado en Zustand + localStorage |
| Búsqueda de productos | Algolia o ElasticSearch |
| Imágenes de productos | Cloudinary + Next.js Image |

### Para Sistema Financiero
| Necesidad | Tecnología |
|---|---|
| Gráficos | Recharts o Chart.js |
| Exportación | xlsx (SheetJS) para Excel, jsPDF para PDF |
| Tablas complejas | TanStack Table v8 |

### Para IA (Futuro)
| Necesidad | Tecnología |
|---|---|
| Chat con IA | Claude API (claude-sonnet) o OpenAI |
| Embeddings / búsqueda semántica | Supabase pgvector o Pinecone |
| Asistente de scheduling | Langchain.js |

---

## Variables de Entorno del Frontend

```env
# .env.local
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_APP_URL=http://localhost:3000

# Producción
NEXT_PUBLIC_API_URL=https://api.tunegocio.com
NEXT_PUBLIC_APP_URL=https://app.tunegocio.com
```

---

## Checklist de Integración Frontend-Backend

- [ ] CORS configurado correctamente (incluir dominio de producción)
- [ ] Axios interceptor para adjuntar Bearer token
- [ ] Axios interceptor para refresh automático en 401
- [ ] Zustand store con persist (localStorage para auth)
- [ ] Route guards: redirigir a /login si no autenticado
- [ ] Manejo de errores API → toast notifications
- [ ] Tipos TypeScript desde API docs de OpenAPI
- [ ] Variables de entorno por ambiente (dev, prod)
- [ ] Widget de booking probado en dominio externo (CORS)
- [ ] Formato de fechas consistente (ISO 8601, timezone del tenant)
