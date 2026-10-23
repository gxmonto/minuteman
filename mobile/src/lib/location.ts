import * as Location from 'expo-location';
import { Platform } from 'react-native';

import type { LocationInput } from './api';

export async function requestLocationPermissions(): Promise<{ foreground: boolean; background: boolean }> {
  if (Platform.OS === 'web') {
    return { foreground: false, background: false };
  }
  const foreground = await Location.requestForegroundPermissionsAsync();
  if (!foreground.granted) {
    return { foreground: false, background: false };
  }
  // Geofencing needs "Allow all the time" / "Always"; the OS asks separately for it.
  const background = await Location.requestBackgroundPermissionsAsync();
  return { foreground: true, background: background.granted };
}

export async function hasBackgroundPermission(): Promise<boolean> {
  if (Platform.OS === 'web') return false;
  return (await Location.getBackgroundPermissionsAsync()).granted;
}

/**
 * Best-effort position for clock-in/out and timer events. Never blocks the action for long, and
 * returns null when permission is missing or GPS is unavailable.
 */
export async function quickPosition(timeoutMs = 5_000): Promise<LocationInput | null> {
  if (Platform.OS === 'web') return null;
  try {
    const permission = await Location.getForegroundPermissionsAsync();
    if (!permission.granted) return null;
    const recent = await Location.getLastKnownPositionAsync({ maxAge: 60_000, requiredAccuracy: 200 });
    const position =
      recent ??
      (await Promise.race([
        Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
        new Promise<null>((resolve) => setTimeout(() => resolve(null), timeoutMs)),
      ]));
    return position ? toInput(position) : null;
  } catch {
    return null;
  }
}

export function toInput(position: Location.LocationObject): LocationInput {
  return {
    latitude: position.coords.latitude,
    longitude: position.coords.longitude,
    accuracyMeters: position.coords.accuracy ?? null,
  };
}
