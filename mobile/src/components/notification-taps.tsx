import { router } from 'expo-router';
import { useLastNotificationResponse } from 'expo-notifications';
import { useEffect } from 'react';

/** Tapping a reminder ("you left the site", "timer still running") opens the Today screen. */
export function NotificationTaps() {
  const lastNotification = useLastNotificationResponse();
  useEffect(() => {
    if (lastNotification) {
      router.navigate('/');
    }
  }, [lastNotification]);
  return null;
}
