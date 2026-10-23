import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { ApiError, createApi, normalizeServerUrl, type Api, type User } from './api';
import { stopSiteGeofence } from './geofence';
import { registerForPush, unregisterPush } from './notifications';
import { storage } from './storage';

type SessionState =
  | { status: 'loading' }
  | { status: 'signedOut'; serverUrl: string | null }
  | { status: 'signedIn'; serverUrl: string; token: string; user: User };

interface SessionValue {
  state: SessionState;
  /** Only meaningful while signed in. */
  api: Api;
  signIn: (serverUrl: string, email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const SessionContext = createContext<SessionValue | null>(null);

export const DEFAULT_SERVER_URL = process.env.EXPO_PUBLIC_API_URL ?? '';

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'loading' });

  const clearSession = useCallback(async () => {
    await storage.set('token', null);
    await storage.set('user', null);
    await stopSiteGeofence().catch(() => undefined);
    setState((s) => ({ status: 'signedOut', serverUrl: s.status === 'signedIn' ? s.serverUrl : null }));
  }, []);

  const api = useMemo(
    () =>
      state.status === 'signedIn'
        ? createApi({ baseUrl: state.serverUrl, token: state.token, onUnauthorized: () => void clearSession() })
        : createApi({ baseUrl: '', token: null }),
    [state, clearSession],
  );

  useEffect(() => {
    (async () => {
      const [serverUrl, token, userJson] = await Promise.all([
        storage.get('serverUrl'),
        storage.get('token'),
        storage.get('user'),
      ]);
      if (!serverUrl || !token || !userJson) {
        setState({ status: 'signedOut', serverUrl });
        return;
      }
      // Stay signed in offline with the cached user; a 401 later sends the user to the login screen.
      setState({ status: 'signedIn', serverUrl, token, user: JSON.parse(userJson) as User });
      try {
        const { user } = await createApi({ baseUrl: serverUrl, token }).me();
        await storage.set('user', JSON.stringify(user));
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          await clearSession();
        }
      }
    })();
  }, [clearSession]);

  const signIn = useCallback(async (rawUrl: string, email: string, password: string) => {
    const serverUrl = normalizeServerUrl(rawUrl);
    const { accessToken, user } = await createApi({ baseUrl: serverUrl, token: null }).login(email.trim(), password);
    await storage.set('serverUrl', serverUrl);
    await storage.set('token', accessToken);
    await storage.set('user', JSON.stringify(user));
    setState({ status: 'signedIn', serverUrl, token: accessToken, user });
    registerForPush(createApi({ baseUrl: serverUrl, token: accessToken })).catch((e) =>
      console.warn('Push registration failed', e),
    );
  }, []);

  const signOut = useCallback(async () => {
    await unregisterPush(api);
    await clearSession();
  }, [api, clearSession]);

  const value = useMemo(() => ({ state, api, signIn, signOut }), [state, api, signIn, signOut]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession must be used inside SessionProvider');
  return value;
}
