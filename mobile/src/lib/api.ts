/** Typed client for the Minuteman backend (see ../../README.md for the API). */

export type Role = 'TECHNICIAN' | 'ADMIN';
export type TimerType = 'TASK' | 'TRAVEL';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';
export type SyncStatus = 'NOT_REQUIRED' | 'PENDING' | 'SYNCED' | 'FAILED';

export interface User {
  id: number;
  email: string;
  fullName: string | null;
  role: Role;
}

export interface Site {
  id: number;
  source: string;
  externalId: string | null;
  name: string;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  radiusMeters: number | null;
}

export interface Task {
  id: number;
  source: string;
  externalId: string | null;
  title: string;
  description: string | null;
  status: TaskStatus;
  dueAt: string | null;
  site: Site | null;
}

export interface Timer {
  id: number;
  type: TimerType;
  taskId: number | null;
  taskTitle: string | null;
  siteId: number | null;
  site: Site | null;
  startTime: string;
  endTime: string | null;
  durationSeconds: number | null;
  notes: string | null;
  leftSiteAt: string | null;
  syncStatus: SyncStatus;
}

export interface WorkDay {
  id: number;
  clockIn: string;
  clockOut: string | null;
  status: 'OPEN' | 'CLOSED';
}

export interface CurrentStatus {
  workDayOpen: boolean;
  workDay: WorkDay | null;
  timer: Timer | null;
  secondsRunning: number;
  totalSecondsToday: number;
  serverTime: string;
}

export interface LocationInput {
  latitude: number;
  longitude: number;
  accuracyMeters?: number | null;
}

export interface LocationReport {
  timerRunning: boolean;
  siteId: number | null;
  distanceMeters: number | null;
  insideSite: boolean | null;
  notified: boolean;
}

export interface LoginResponse {
  accessToken: string;
  expiresAt: string;
  user: User;
}

export interface StartTimerInput {
  type: TimerType;
  taskId?: number | null;
  notes?: string | null;
  location?: LocationInput | null;
}

/** Error returned by the backend (problem+json with a stable `code`) or a network failure (`status` 0). */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isNetworkError() {
    return this.status === 0;
  }
}

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email o contraseña incorrectos.',
  WORKDAY_ALREADY_OPEN: 'Tu jornada ya está iniciada.',
  NO_OPEN_WORKDAY: 'Primero inicia tu jornada.',
  TIMER_ALREADY_RUNNING: 'Ya tienes un timer corriendo. Detenlo primero.',
  NO_RUNNING_TIMER: 'No hay ningún timer corriendo.',
  TASK_REQUIRED: 'Elige una tarea.',
  TASK_NOT_FOUND: 'La tarea ya no existe.',
  TASK_NOT_ASSIGNED: 'Esta tarea no está asignada a ti.',
  TASK_CLOSED: 'Esta tarea ya está cerrada.',
  CONFLICT: 'Otra acción cambió tus datos al mismo tiempo. Intenta de nuevo.',
  CONCURRENT_UPDATE: 'Otra acción cambió tus datos al mismo tiempo. Intenta de nuevo.',
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isNetworkError) {
      return 'No hay conexión con el servidor. Revisa tu señal e intenta de nuevo.';
    }
    if (error.status === 401) {
      return error.code === 'INVALID_CREDENTIALS' ? MESSAGES.INVALID_CREDENTIALS : 'Tu sesión expiró. Vuelve a entrar.';
    }
    return (error.code && MESSAGES[error.code]) || error.message || 'Algo salió mal.';
  }
  return error instanceof Error ? error.message : 'Algo salió mal.';
}

export function normalizeServerUrl(url: string): string {
  let value = url.trim().replace(/\/+$/, '');
  if (value && !/^https?:\/\//i.test(value)) {
    value = `https://${value}`;
  }
  return value;
}

const TIMEOUT_MS = 15_000;

export interface ApiConfig {
  baseUrl: string;
  token: string | null;
  /** Called on 401 for authenticated calls, so the app can send the user back to the login screen. */
  onUnauthorized?: () => void;
}

export function createApi({ baseUrl, token, onUnauthorized }: ApiConfig) {
  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
    let response: Response;
    try {
      response = await fetch(`${baseUrl}${path}`, {
        method,
        headers: {
          Accept: 'application/json',
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });
    } catch {
      throw new ApiError('Network error', 0, null);
    } finally {
      clearTimeout(timeout);
    }

    if (response.status === 204) {
      return undefined as T;
    }
    const text = await response.text();
    const json = text ? safeParse(text) : null;
    if (!response.ok) {
      if (response.status === 401 && token) {
        onUnauthorized?.();
      }
      const problem = (json ?? {}) as { detail?: string; title?: string; code?: string };
      throw new ApiError(problem.detail || problem.title || `HTTP ${response.status}`, response.status, problem.code ?? null);
    }
    return json as T;
  }

  return {
    login: (email: string, password: string) =>
      request<LoginResponse>('POST', '/api/auth/login', { email, password }),
    me: () => request<{ user: User }>('GET', '/api/me'),

    currentStatus: () => request<CurrentStatus>('GET', '/api/work/current'),
    startDay: (location: LocationInput | null) => request<WorkDay>('POST', '/api/work/day/start', { location }),
    endDay: (location: LocationInput | null) => request<WorkDay>('POST', '/api/work/day/end', { location }),
    startTimer: (input: StartTimerInput) => request<Timer>('POST', '/api/work/timers/start', input),
    stopTimer: (notes: string | null, location: LocationInput | null) =>
      request<Timer>('POST', '/api/work/timers/stop', { notes, location }),
    reportLocation: (location: LocationInput) => request<LocationReport>('POST', '/api/work/location', location),

    myTasks: () => request<Task[]>('GET', '/api/tasks'),
    refreshTasks: () => request<Task[]>('POST', '/api/tasks/refresh'),

    registerDevice: (pushToken: string, platform: 'android' | 'ios' | 'web') =>
      request<void>('POST', '/api/me/devices', { token: pushToken, platform }),
    unregisterDevice: (pushToken: string) => request<void>('DELETE', '/api/me/devices', { token: pushToken }),
  };
}

export type Api = ReturnType<typeof createApi>;

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
