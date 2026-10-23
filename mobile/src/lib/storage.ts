import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Token and settings live in the OS keychain/keystore. The background geofence task reads them from
 * here too, because no React state exists when the OS wakes the app in the background.
 */
const KEYS = {
  serverUrl: 'minuteman.serverUrl',
  token: 'minuteman.token',
  user: 'minuteman.user',
  pushToken: 'minuteman.pushToken',
} as const;

type Key = keyof typeof KEYS;

async function get(key: Key): Promise<string | null> {
  if (Platform.OS === 'web') {
    return globalThis.localStorage?.getItem(KEYS[key]) ?? null;
  }
  return SecureStore.getItemAsync(KEYS[key]);
}

async function set(key: Key, value: string | null): Promise<void> {
  if (Platform.OS === 'web') {
    if (value === null) globalThis.localStorage?.removeItem(KEYS[key]);
    else globalThis.localStorage?.setItem(KEYS[key], value);
    return;
  }
  if (value === null) await SecureStore.deleteItemAsync(KEYS[key]);
  else await SecureStore.setItemAsync(KEYS[key], value);
}

export const storage = { get, set };
