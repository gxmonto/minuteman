# Minuteman, app móvil

App para Android e iOS (con Expo / React Native) que usan los técnicos de campo. Desde el mismo código también se genera una versión web.

- Login contra el servidor de Minuteman (la URL se puede cambiar en la pantalla de login).
- **Hoy**: iniciar y terminar jornada, timer de tarea o de traslado con notas, total del día en vivo.
- **Tareas**: tareas asignadas, de Bitrix24 u otro sistema conectado, o creadas en Minuteman. Deslizar hacia abajo las vuelve a pedir al sistema externo.
- **Geocerca**: mientras corre un timer de tarea, el teléfono vigila el sitio aunque la app esté cerrada. Si el técnico sale, la app avisa al servidor y llega la notificación *"¿Terminaste la tarea?"*. Sin señal, el teléfono muestra el aviso por su cuenta.
- **Recordatorios** del servidor por timers o jornadas olvidadas (push).
- Registra la ubicación al marcar entrada, salida, inicio y fin de cada timer, si el técnico dio permiso.

## Estructura

```
src/app/            pantallas (Expo Router): login, (app)/index = Hoy, (app)/tasks = Tareas
src/lib/api.ts      cliente tipado de la API y mensajes de error en español
src/lib/session.tsx sesión: token en el llavero del sistema (SecureStore)
src/lib/work.tsx    estado de jornada, timer y tareas compartido por las pantallas
src/lib/geofence.ts tarea en segundo plano de la geocerca
src/lib/notifications.ts  registro del push y avisos locales
```

## Desarrollo

```bash
npm install
cp .env.example .env.local     # URL del servidor que aparece de entrada en el login
npm run typecheck && npm run lint && npm test
```

La geocerca en segundo plano y las notificaciones push **no funcionan en Expo Go**. Hace falta un *development build*, que se instala una vez en el teléfono:

```bash
npx eas-cli@latest login
npx eas-cli@latest init                                  # crea el proyecto EAS y su projectId (necesario para push)
npx eas-cli@latest build --profile development --platform android   # o ios
npx expo start                                          # luego abre la app instalada
```

Para probar solo pantallas y flujo sin teléfono: `npm run web`. El backend necesita `CORS_ALLOWED_ORIGINS=http://localhost:8081`.

El teléfono debe poder llegar al backend. Android bloquea `http://` sin cifrar fuera de desarrollo, así que usa HTTPS (por ejemplo, un túnel como Cloudflare Tunnel o ngrok mientras pruebas).

## Notificaciones push

La app registra su *Expo push token* en el backend (`POST /api/me/devices`) y el backend envía con el servicio de Expo. Para que lleguen:

- **Android**: sube la clave de FCM del proyecto de Firebase a EAS (`eas credentials`).
- **iOS**: EAS configura la clave de APNs al hacer el primer build de iOS (requiere cuenta de Apple Developer).

Si activas *enhanced push security* en Expo, define `EXPO_ACCESS_TOKEN` en el backend.

## Publicar

```bash
npx eas-cli@latest build --profile preview --platform android      # APK para repartir directo a los técnicos
npx eas-cli@latest build --profile production --platform all       # para Play Store / App Store
npx eas-cli@latest submit --platform android|ios
```

Las tiendas piden justificar la ubicación en segundo plano. El texto de permiso ya explica que solo se usa mientras hay un timer de tarea abierto (ver `app.json`). Google Play además pide un video corto mostrando la función.
