import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';

import type { CurrentStatus, StartTimerInput, Task } from './api';
import { syncGeofence } from './geofence';
import { quickPosition } from './location';
import { useSession } from './session';

type GeofenceState = 'watching' | 'idle' | 'no-permission';

interface WorkValue {
  status: CurrentStatus | null;
  /** serverTime - phone time, for ticking timers with the server's clock. */
  clockOffsetMs: number;
  tasks: Task[];
  geofence: GeofenceState;
  loading: boolean;
  refreshStatus: () => Promise<void>;
  loadTasks: (fromExternalSystems?: boolean) => Promise<void>;
  startDay: () => Promise<void>;
  endDay: () => Promise<void>;
  startTimer: (input: Omit<StartTimerInput, 'location'>) => Promise<void>;
  stopTimer: (notes: string | null) => Promise<void>;
}

const WorkContext = createContext<WorkValue | null>(null);

const REFRESH_MS = 60_000;

export function WorkProvider({ children }: { children: ReactNode }) {
  const { api, state } = useSession();
  const [status, setStatus] = useState<CurrentStatus | null>(null);
  const [clockOffsetMs, setClockOffsetMs] = useState(0);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [geofence, setGeofence] = useState<GeofenceState>('idle');
  const [loading, setLoading] = useState(true);
  const signedIn = state.status === 'signedIn';

  const apply = useCallback(async (next: CurrentStatus) => {
    setStatus(next);
    setClockOffsetMs(new Date(next.serverTime).getTime() - Date.now());
    setGeofence(await syncGeofence(next.timer).catch(() => 'idle' as const));
  }, []);

  const refreshStatus = useCallback(async () => {
    try {
      await apply(await api.currentStatus());
    } finally {
      setLoading(false);
    }
  }, [api, apply]);

  const loadTasks = useCallback(async (fromExternalSystems = false) => {
    const list = fromExternalSystems ? await api.refreshTasks() : await api.myTasks();
    setTasks(list);
  }, [api]);

  useEffect(() => {
    if (!signedIn) return;
    refreshStatus().catch(() => undefined);
    api.myTasks().then(setTasks, () => undefined);
    const interval = setInterval(() => refreshStatus().catch(() => undefined), REFRESH_MS);
    const sub = AppState.addEventListener('change', (s) => {
      if (s === 'active') refreshStatus().catch(() => undefined);
    });
    return () => {
      clearInterval(interval);
      sub.remove();
    };
  }, [signedIn, api, refreshStatus]);

  const startDay = useCallback(async () => {
    await api.startDay(await quickPosition());
    await refreshStatus();
  }, [api, refreshStatus]);

  const endDay = useCallback(async () => {
    await api.endDay(await quickPosition());
    await refreshStatus();
  }, [api, refreshStatus]);

  const startTimer = useCallback(
    async (input: Omit<StartTimerInput, 'location'>) => {
      await api.startTimer({ ...input, location: await quickPosition() });
      await refreshStatus();
    },
    [api, refreshStatus],
  );

  const stopTimer = useCallback(
    async (notes: string | null) => {
      await api.stopTimer(notes, await quickPosition());
      await refreshStatus();
    },
    [api, refreshStatus],
  );

  const value = useMemo(
    () => ({
      status,
      clockOffsetMs,
      tasks,
      geofence,
      loading,
      refreshStatus,
      loadTasks,
      startDay,
      endDay,
      startTimer,
      stopTimer,
    }),
    [status, clockOffsetMs, tasks, geofence, loading, refreshStatus, loadTasks, startDay, endDay, startTimer, stopTimer],
  );
  return <WorkContext.Provider value={value}>{children}</WorkContext.Provider>;
}

export function useWork(): WorkValue {
  const value = useContext(WorkContext);
  if (!value) throw new Error('useWork must be used inside WorkProvider');
  return value;
}

/** Re-renders every second while `active`, for ticking timers. */
export function useTicker(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [active]);
  return now;
}
