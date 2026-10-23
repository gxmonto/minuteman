// Registers the background geofence task at load time, before any screen mounts.
import '@/lib/geofence';

import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { useColorScheme } from 'react-native';

import { NotificationTaps } from '@/components/notification-taps';
import { configureNotifications } from '@/lib/notifications';
import { SessionProvider, useSession } from '@/lib/session';
import { WorkProvider } from '@/lib/work';

SplashScreen.preventAutoHideAsync();
configureNotifications();

export default function RootLayout() {
  const scheme = useColorScheme();
  return (
    <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
      <SessionProvider>
        <RootNavigator />
      </SessionProvider>
    </ThemeProvider>
  );
}

function RootNavigator() {
  const { state } = useSession();
  const signedIn = state.status === 'signedIn';

  useEffect(() => {
    if (state.status !== 'loading') {
      SplashScreen.hideAsync();
    }
  }, [state.status]);

  if (state.status === 'loading') {
    return null;
  }
  // Keyed by user so nothing from a previous session survives a sign-out/sign-in.
  return (
    <WorkProvider key={signedIn ? state.user.id : 'signed-out'}>
      {signedIn ? <NotificationTaps /> : null}
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Protected guard={signedIn}>
          <Stack.Screen name="(app)" />
        </Stack.Protected>
        <Stack.Protected guard={!signedIn}>
          <Stack.Screen name="login" />
        </Stack.Protected>
      </Stack>
    </WorkProvider>
  );
}
