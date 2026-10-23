import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { AndroidImportance } from 'expo-notifications';
import { Platform } from 'react-native';

import type { Api } from './api';
import { storage } from './storage';

export const REMINDER_CHANNEL = 'reminders';

/** Show reminders even while the app is open. Called once from the root layout. */
export function configureNotifications() {
  if (Platform.OS === 'web') return;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

async function ensureAndroidChannel() {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(REMINDER_CHANNEL, {
      name: 'Recordatorios de timer',
      importance: AndroidImportance.HIGH,
    });
  }
}

/**
 * Asks for notification permission and registers this device's Expo push token with the backend,
 * which uses it for "you left the site" and "timer still running" reminders.
 */
export async function registerForPush(api: Api): Promise<string | null> {
  if (Platform.OS === 'web' || !Device.isDevice) {
    return null;
  }
  await ensureAndroidChannel();
  let { granted } = await Notifications.getPermissionsAsync();
  if (!granted) {
    ({ granted } = await Notifications.requestPermissionsAsync());
  }
  if (!granted) {
    return null;
  }
  const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) {
    console.warn('No EAS projectId configured; push notifications are disabled. Run `npx eas-cli init`.');
    return null;
  }
  const { data: pushToken } = await Notifications.getExpoPushTokenAsync({ projectId });
  await api.registerDevice(pushToken, Platform.OS === 'ios' ? 'ios' : 'android');
  await storage.set('pushToken', pushToken);
  return pushToken;
}

export async function unregisterPush(api: Api): Promise<void> {
  const pushToken = await storage.get('pushToken');
  if (pushToken) {
    await api.unregisterDevice(pushToken).catch(() => undefined);
    await storage.set('pushToken', null);
  }
}

/** Shown by the phone itself, e.g. when the geofence fires but the server can't be reached. */
export async function showLocalNotification(title: string, body: string, data: Record<string, string> = {}) {
  if (Platform.OS === 'web') return;
  await ensureAndroidChannel();
  await Notifications.scheduleNotificationAsync({
    content: { title, body, data, sound: true },
    trigger: Platform.OS === 'android' ? { channelId: REMINDER_CHANNEL } : null,
  });
}
