/** 3725 -> "1:02:05"; 125 -> "2:05". */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const mm = String(m).padStart(h > 0 ? 2 : 1, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** 3725 -> "1 h 02 min"; 300 -> "5 min". */
export function formatHours(totalSeconds: number): string {
  const minutes = Math.floor(Math.max(0, totalSeconds) / 60);
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h} h ${String(m).padStart(2, '0')} min` : `${m} min`;
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '--:--';
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? '--:--'
    : date.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
}

export function formatDate(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? null
    : date.toLocaleDateString('es', { weekday: 'short', day: 'numeric', month: 'short' });
}

/**
 * Seconds since `startIso` using the server's clock, so a phone with the wrong time still shows the
 * right elapsed time. `offsetMs` is serverTime - phoneTime measured at the last status fetch.
 */
export function secondsSince(startIso: string, offsetMs: number, nowMs = Date.now()): number {
  return Math.max(0, Math.floor((nowMs + offsetMs - new Date(startIso).getTime()) / 1000));
}
