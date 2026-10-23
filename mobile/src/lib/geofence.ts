import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { Platform } from 'react-native';

import { createApi, type LocationInput, type LocationReport, type Site, type Timer } from './api';
import { hasBackgroundPermission, toInput } from './location';
import { showLocalNotification } from './notifications';
import { storage } from './storage';

/**
 * While a TASK timer runs, the OS watches the task site's geofence. On exit it wakes the app (even in
 * the background) and we report the position; the server decides and sends the push notification.
 * If the server can't be reached we notify locally instead, so the technician is still reminded.
 */
export const GEOFENCE_TASK = 'minuteman-site-geofence';

/** Android and iOS are unreliable with very small regions. */
const MIN_RADIUS_METERS = 100;

type GeofenceEvent = { eventType: Location.GeofencingEventType; region: Location.LocationRegion };

export interface ExitDeps {
  loadConfig: () => Promise<{ baseUrl: string | null; token: string | null }>;
  currentPosition: () => Promise<LocationInput | null>;
  report: (baseUrl: string, token: string, location: LocationInput) => Promise<LocationReport>;
  notifyLocally: (title: string, body: string) => Promise<void>;
  stopWatching: () => Promise<void>;
}

const LOCAL_TITLE = '¿Terminaste la tarea?';
const LOCAL_BODY = 'Saliste del sitio y tu timer sigue corriendo. Ábrelo para detenerlo.';

export async function handleSiteExit(deps: ExitDeps): Promise<void> {
  const { baseUrl, token } = await deps.loadConfig();
  if (!baseUrl || !token) {
    await deps.stopWatching();
    return;
  }
  const position = await deps.currentPosition();
  if (!position) {
    await deps.notifyLocally(LOCAL_TITLE, LOCAL_BODY);
    return;
  }
  try {
    const report = await deps.report(baseUrl, token, position);
    if (!report.timerRunning) {
      // The timer was stopped elsewhere (e.g. from another device); nothing left to watch.
      await deps.stopWatching();
    }
  } catch {
    await deps.notifyLocally(LOCAL_TITLE, LOCAL_BODY);
  }
}

const defaultDeps: ExitDeps = {
  loadConfig: async () => ({ baseUrl: await storage.get('serverUrl'), token: await storage.get('token') }),
  currentPosition: async () => {
    try {
      return toInput(await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }));
    } catch {
      const last = await Location.getLastKnownPositionAsync();
      return last ? toInput(last) : null;
    }
  },
  report: (baseUrl, token, location) => createApi({ baseUrl, token }).reportLocation(location),
  notifyLocally: (title, body) => showLocalNotification(title, body, { type: 'LEFT_SITE' }),
  stopWatching: () => stopSiteGeofence(),
};

// Must run at module load (not inside a component): the OS may start the app headless for this task.
if (Platform.OS !== 'web') {
  TaskManager.defineTask<GeofenceEvent>(GEOFENCE_TASK, async ({ data, error }) => {
    if (error || !data || data.eventType !== Location.GeofencingEventType.Exit) {
      return;
    }
    await handleSiteExit(defaultDeps);
  });
}

export function geofenceFor(site: Site | null | undefined): Location.LocationRegion | null {
  if (!site || site.latitude == null || site.longitude == null) {
    return null;
  }
  return {
    identifier: `site-${site.id}`,
    latitude: site.latitude,
    longitude: site.longitude,
    radius: Math.max(site.radiusMeters ?? 150, MIN_RADIUS_METERS),
    notifyOnEnter: false,
    notifyOnExit: true,
  };
}

export async function stopSiteGeofence(): Promise<void> {
  if (Platform.OS === 'web') return;
  if (await Location.hasStartedGeofencingAsync(GEOFENCE_TASK)) {
    await Location.stopGeofencingAsync(GEOFENCE_TASK);
  }
}

/** Watch the site of the running TASK timer, or nothing. Safe to call on every status refresh. */
export async function syncGeofence(timer: Timer | null): Promise<'watching' | 'idle' | 'no-permission'> {
  if (Platform.OS === 'web') return 'idle';
  const region = timer?.type === 'TASK' ? geofenceFor(timer.site) : null;
  if (!region) {
    await stopSiteGeofence();
    return 'idle';
  }
  if (!(await hasBackgroundPermission())) {
    return 'no-permission';
  }
  // Re-registering replaces the previous region set for this task.
  await Location.startGeofencingAsync(GEOFENCE_TASK, [region]);
  return 'watching';
}
