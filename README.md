# Minuteman

Backend de control de tiempo para técnicos de campo: jornada (clock in/out), timers por tarea o traslado, geocercas por sitio con aviso cuando el técnico se aleja con un timer abierto, y recordatorios de timers o jornadas olvidadas.

Las tareas pueden venir de **cualquier sistema externo** (Bitrix24, un CRM, un ERP, una API propia) y las horas registradas regresan a ese sistema. También se pueden crear tareas directamente en Minuteman.

## Arquitectura

```
App móvil ──JWT──▶  /api/auth, /api/me, /api/tasks, /api/work       (técnicos)
Back-office ─JWT─▶  /api/admin/**                                   (ROLE_ADMIN)
Sistema externo ─X-Api-Key─▶ /api/integrations/inbound/{provider}/** (push de tareas / pull de horas)

Minuteman ──conector──▶ Bitrix24 / HTTP genérico / tu propio IntegrationConnector
```

| Paquete | Responsabilidad |
|---|---|
| `auth` | Login, emisión y validación de JWT (HS256). El usuario siempre sale del token, nunca de la URL. |
| `work` | Jornada, timers y reportes de ubicación (geocerca). |
| `task` | Tareas asignadas al técnico. |
| `integration` | Conectores, importación de tareas, outbox de horas y API inbound. |
| `notification` | Tokens de dispositivo, push (FCM) y recordatorios programados. |
| `admin` | Usuarios, vínculos con cuentas externas, sitios/geocercas, tareas locales, estado de sincronización. |

## Cómo correrlo

Requisitos: Java 25 (el `pom.xml` lo pide; el código también compila con 21 usando `-Djava.version=21`) y MySQL 8.

```bash
cp .env.example .env        # y completa los valores
set -a; source .env; set +a
./mvnw spring-boot:run
```

Flyway crea el esquema al iniciar (`src/main/resources/db/migration`). **Si ya tenías una base `timetracker` creada por la versión anterior** (con `ddl-auto=update`), usa una base nueva o bórrala: el esquema cambió (fechas en UTC, tareas, sitios con coordenadas, etc.).

Si `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` están definidos y no hay usuarios, se crea el primer admin.

Tests (usan H2 en memoria con las mismas migraciones, no necesitan MySQL):

```bash
./mvnw test
```

## API para la app móvil

Todas las rutas (excepto login) llevan `Authorization: Bearer <token>`. Los errores siguen RFC 9457 con un `code` estable para la app, por ejemplo:

```json
{"status":409,"title":"Conflict","detail":"Stop the running timer first","code":"TIMER_ALREADY_RUNNING"}
```

| Método y ruta | Cuerpo | Notas |
|---|---|---|
| `POST /api/auth/login` | `{email, password}` | Devuelve `accessToken` y `expiresAt`. |
| `GET /api/me` | | Usuario y cuentas externas vinculadas. |
| `POST /api/me/devices` | `{token, platform}` | Registra el token FCM (`android`, `ios` o `web`). |
| `DELETE /api/me/devices` | `{token}` | Al cerrar sesión. |
| `GET /api/tasks` | | Tareas abiertas asignadas, con sitio y geocerca. |
| `POST /api/tasks/refresh` | | Pide las tareas al sistema externo y devuelve la lista (pull-to-refresh). |
| `POST /api/work/day/start` | `{location?}` | Clock in. |
| `POST /api/work/day/end` | `{location?}` | Clock out; detiene el timer si hay uno corriendo. |
| `POST /api/work/timers/start` | `{type: TASK\|TRAVEL, taskId?, notes?, location?}` | `TASK` requiere `taskId`. |
| `POST /api/work/timers/stop` | `{notes?, location?}` | |
| `GET /api/work/current` | | Estado de jornada y timer, `totalSecondsToday` y `serverTime`. |
| `POST /api/work/location` | `{latitude, longitude, accuracyMeters?}` | Ver geocercas abajo. |

`location` es `{latitude, longitude, accuracyMeters?}`. Todas las fechas son instantes UTC (ISO-8601); la app las muestra en hora local.

### Geocercas y avisos

1. Al iniciar un timer `TASK`, la app registra en el sistema operativo una geocerca con `site.latitude/longitude/radiusMeters` (iOS: Core Location region monitoring; Android: Geofencing API; Expo: `Location.startGeofencingAsync`). Así funciona en segundo plano sin gastar batería.
2. Cuando el sistema operativo dispara la salida de la geocerca (y, si quieres, cada pocos minutos mientras el timer corre), la app llama `POST /api/work/location`.
3. El servidor calcula la distancia. Si el técnico está fuera de `radio + max(tolerancia, precisión GPS)`, guarda `leftSiteAt` y envía un push: *"¿Terminaste en X? Saliste del sitio y el timer sigue corriendo"*. No repite el aviso antes de `left-site-repeat-every` (15 min por defecto) y se rearma cuando el técnico vuelve al sitio.
4. Además, un job del servidor avisa de timers abiertos más de `timer-open-after` (4 h) y jornadas abiertas más de `work-day-open-after` (12 h), aunque el teléfono no haya reportado nada.

Los textos de las notificaciones están en `messages.properties` (español) y `messages_en.properties`; el idioma se elige con `APP_LOCALE`.

## Conectar un sistema externo

Cada sistema es un *provider* con una clave (`bitrix24`, `myerp`, `mycrm`...). Las tareas y sitios guardan su `source` y `externalId`. Cada técnico se vincula con su id en ese sistema:

```http
PUT /api/admin/users/{id}/external-accounts/{provider}
{"externalUserId": "17"}
```

Hay tres formas de integrar, combinables:

### 1. Bitrix24 (incluido)

```properties
BITRIX24_ENABLED=true
BITRIX24_WEBHOOK_URL=https://tu-portal.bitrix24.com/rest/1/xxxxxxxx/
BITRIX24_SITE_FIELD=UF_AUTO_123456   # opcional: campo de la tarea con el nombre/código del sitio
```

- Crea un webhook entrante en Bitrix24 con el scope **tasks**. El usuario dueño del webhook necesita permiso para registrar tiempo en nombre de otros.
- Cada 10 minutos (o con `refresh` / `POST /api/admin/integrations/bitrix24/sync`) trae las tareas abiertas de cada técnico vinculado (`tasks.task.list`, responsable = técnico).
- Al detener un timer, las horas se registran en la tarea con `task.elapseditem.add`, en el registro de tiempo nativo de Bitrix. Así siguen viéndose en Bitrix sin usar su timer.
- Bitrix no guarda coordenadas: define la geocerca de cada sitio una vez con `PUT /api/admin/sites/{id}/geofence`. Si una tarea no trae sitio, asígnalo con `PUT /api/admin/tasks/{id}/site`. Las sincronizaciones siguientes respetan lo que configure el admin.

> El conector está probado contra respuestas simuladas, no contra un portal real. Conviene validarlo primero con un webhook de pruebas.

### 2. Conector HTTP genérico (sin código)

Para cualquier sistema que pueda exponer dos endpoints, directamente o con un adaptador ligero (n8n, Zapier, Make, una función serverless):

```properties
timetrack.integrations.http.myerp.tasks-url=https://erp.example.com/minuteman/tasks
timetrack.integrations.http.myerp.time-entries-url=https://erp.example.com/minuteman/time-entries
timetrack.integrations.http.myerp.headers.Authorization=Bearer ${MYERP_TOKEN}
```

- `GET tasks-url?externalUserId=17` debe responder un arreglo de tareas:

  ```json
  [{
    "externalId": "WO-1001",
    "title": "Instalar router",
    "description": "Piso 2",
    "status": "OPEN",
    "dueAt": "2026-10-25T15:00:00Z",
    "assigneeExternalUserIds": ["17", "23"],
    "site": {"externalId": "BR-12", "name": "Sucursal Centro", "address": "...",
             "latitude": 19.4326, "longitude": -99.1332, "radiusMeters": 120}
  }]
  ```

  `status` puede ser `OPEN`, `IN_PROGRESS`, `DONE` o `CANCELLED`. Todo es opcional excepto `externalId` y `title`.

- `POST time-entries-url` (opcional) recibe cada registro de tiempo cerrado y puede responder `{"id": "..."}`:

  ```json
  {"id": 42, "provider": "myerp", "taskExternalId": "WO-1001", "userExternalId": "17",
   "userEmail": "tech@example.com", "type": "TASK", "startedAt": "...", "endedAt": "...",
   "durationSeconds": 2700, "notes": "Cambié el filtro", "startLocation": {...}, "endLocation": {...},
   "leftSiteAt": null, "updatedAt": "..."}
  ```

Puedes declarar varios conectores HTTP; cada nombre es un provider distinto.

### 3. API inbound (el sistema externo llama a Minuteman)

Para sistemas que prefieren empujar sus datos:

```properties
timetrack.integrations.inbound-api-keys.mycrm=${MYCRM_API_KEY}
```

```http
PUT /api/integrations/inbound/mycrm/tasks
X-Api-Key: <clave>
{"tasks": [ ...mismo formato de tarea... ]}      # máximo 500 por llamada

GET /api/integrations/inbound/mycrm/time-entries?cursor=<nextCursor>&limit=100
X-Api-Key: <clave>
→ {"items": [ ...registros de tiempo... ], "nextCursor": "..."}
```

Cada clave solo da acceso a su provider. El cursor es opaco: guarda `nextCursor` y úsalo en la siguiente llamada. Un registro puede repetirse si cambió, así que deduplica por `id`.

### 4. Conector propio en Java

Implementa `IntegrationConnector` (`fetchOpenTasks` y, opcionalmente, `pushTimeEntry`) y regístralo como `@Bean`. El registro lo detecta solo.

### Entrega confiable de horas

Detener un timer nunca falla porque el sistema externo esté caído. El registro queda `PENDING` y un job lo envía cada minuto, con reintentos y backoff exponencial (hasta 6 h entre intentos). Tras `max-push-attempts` intentos queda `FAILED`. El admin lo ve en `GET /api/admin/time-entries/sync-failures` y lo reintenta con `POST /api/admin/time-entries/{id}/retry-sync`, por ejemplo después de vincular la cuenta externa que faltaba.

## Endpoints de administración

| Ruta | Para qué |
|---|---|
| `GET/POST /api/admin/users`, `PATCH /api/admin/users/{id}` | Alta de técnicos, rol, desactivación (efecto inmediato aunque tengan token), cambio de contraseña. |
| `PUT/DELETE /api/admin/users/{id}/external-accounts/{provider}` | Vincular al técnico con su id en el sistema externo. |
| `GET/POST /api/admin/sites`, `PUT /api/admin/sites/{id}/geofence` | Sitios y geocercas. |
| `POST /api/admin/tasks`, `PUT /api/admin/tasks/{id}/site` | Tareas locales y asignación de sitio. |
| `GET /api/admin/integrations`, `POST /api/admin/integrations/{provider}/sync` | Conectores configurados y sincronización manual. |
| `GET /api/admin/time-entries/sync-failures`, `POST /api/admin/time-entries/{id}/retry-sync` | Registros que no se pudieron enviar. |

## Configuración relevante

| Propiedad | Default | |
|---|---|---|
| `timetrack.reminders.timer-open-after` | `PT4H` | Recordar timers abiertos tras este tiempo. |
| `timetrack.reminders.work-day-open-after` | `PT12H` | Recordar jornadas abiertas. |
| `timetrack.reminders.repeat-every` | `PT1H` | Frecuencia máxima de esos recordatorios. |
| `timetrack.reminders.left-site-repeat-every` | `PT15M` | Frecuencia máxima del aviso de salida del sitio. |
| `timetrack.reminders.left-site-tolerance-meters` | `50` | Holgura sobre el radio por deriva del GPS. |
| `timetrack.sites.default-radius-meters` | `150` | Radio para sitios importados sin radio. |
| `timetrack.integrations.task-sync-interval` | `PT10M` | Pull de tareas. |
| `timetrack.integrations.max-push-attempts` | `10` | Intentos antes de marcar `FAILED`. |

## Pendiente

- **App móvil** (React Native/Expo o Flutter): consume la API de arriba.
- Límite de intentos de login (rate limiting) y refresh tokens.
- Reportes de horas por técnico, tarea y periodo.
- Si se corren varias instancias del backend, coordinar los jobs programados (por ejemplo con ShedLock).
