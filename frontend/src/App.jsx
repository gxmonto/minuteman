import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from './api.js';

const TIMER_TYPES = [
  { value: 'TASK', label: 'Task time', description: 'Focused work' },
  { value: 'TRAVEL', label: 'Travel time', description: 'Transit or site travel' }
];

const STORAGE_KEY = 'minuteman.userId';

function readSavedUserId() {
  try {
    return window.localStorage.getItem(STORAGE_KEY) || '1';
  } catch {
    return '1';
  }
}

function saveUserId(value) {
  try {
    window.localStorage.setItem(STORAGE_KEY, value);
  } catch {
    // Ignore storage failures. The app still works without persistence.
  }
}

function asSeconds(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0;
}

function parseDateTime(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDuration(totalSeconds) {
  const seconds = asSeconds(totalSeconds);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;

  if (hours > 0) {
    return `${hours}h ${String(minutes).padStart(2, '0')}m ${String(remainingSeconds).padStart(2, '0')}s`;
  }

  return `${minutes}m ${String(remainingSeconds).padStart(2, '0')}s`;
}

function formatClock(value) {
  const date = parseDateTime(value);
  if (!date) return '--';

  return new Intl.DateTimeFormat(undefined, {
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit'
  }).format(date);
}

function formatDate(value) {
  const date = parseDateTime(value);
  if (!date) return 'Today';

  return new Intl.DateTimeFormat(undefined, {
    weekday: 'long',
    month: 'short',
    day: 'numeric'
  }).format(date);
}

function explainError(error) {
  if (error instanceof TypeError) {
    return `Could not reach the API at ${api.baseUrl}. Check that Spring Boot is running and CORS is enabled.`;
  }

  if (error?.status === 500 && error?.message?.includes('Internal Server Error')) {
    return 'The backend returned a server error. Check that the user ID exists in the database and that the database is running.';
  }

  return error?.message || 'Something went wrong.';
}

function getRunningSeconds(status, fetchedAt, now) {
  if (!status?.timerRunning) return 0;
  const serverSeconds = asSeconds(status.secondsRunning);
  const localSeconds = fetchedAt ? Math.max(0, Math.floor((now - fetchedAt) / 1000)) : 0;
  return serverSeconds + localSeconds;
}

function StatusPill({ tone = 'neutral', children }) {
  return <span className={`status-pill status-pill--${tone}`}>{children}</span>;
}

function ActionButton({ children, variant = 'secondary', isLoading, ...props }) {
  return (
    <button className={`button button--${variant}`} {...props}>
      {isLoading ? 'Working...' : children}
    </button>
  );
}

function MetricCard({ label, value, helper }) {
  return (
    <section className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
      {helper ? <small>{helper}</small> : null}
    </section>
  );
}

export default function App() {
  const [userId, setUserId] = useState(readSavedUserId);
  const [status, setStatus] = useState(null);
  const [statusFetchedAt, setStatusFetchedAt] = useState(null);
  const [now, setNow] = useState(Date.now());
  const [loading, setLoading] = useState(false);
  const [busyAction, setBusyAction] = useState(null);
  const [error, setError] = useState('');
  const [apiHealth, setApiHealth] = useState('checking');
  const [activityLog, setActivityLog] = useState([]);

  const trimmedUserId = userId.trim();
  const hasValidUserId = /^\d+$/.test(trimmedUserId);

  const runningSeconds = useMemo(
    () => getRunningSeconds(status, statusFetchedAt, now),
    [status, statusFetchedAt, now]
  );

  const totalSecondsToday = asSeconds(status?.totalSecondsToday) + runningSeconds;
  const dayStartedAt = status?.clockIn;
  const timerStartedAt = status?.timerStartedAt;
  const daySessionSeconds = useMemo(() => {
    const clockIn = parseDateTime(dayStartedAt);
    if (!clockIn || !status?.workDayOpen) return 0;
    return Math.max(0, Math.floor((now - clockIn.getTime()) / 1000));
  }, [dayStartedAt, now, status?.workDayOpen]);

  const canStartDay = hasValidUserId && !status?.workDayOpen;
  const canEndDay = hasValidUserId && Boolean(status?.workDayOpen);
  const canStartTimer = hasValidUserId && Boolean(status?.workDayOpen) && !status?.timerRunning;
  const canStopTimer = hasValidUserId && Boolean(status?.timerRunning);

  const addActivity = useCallback((message) => {
    const timestamp = new Intl.DateTimeFormat(undefined, {
      hour: 'numeric',
      minute: '2-digit',
      second: '2-digit'
    }).format(new Date());

    setActivityLog((items) => [{ timestamp, message }, ...items].slice(0, 6));
  }, []);

  const refreshStatus = useCallback(
    async ({ silent = false } = {}) => {
      if (!hasValidUserId) {
        setStatus(null);
        setStatusFetchedAt(null);
        setError('Enter a numeric user ID.');
        return;
      }

      if (!silent) setLoading(true);

      try {
        const currentStatus = await api.getCurrentStatus(trimmedUserId);
        setStatus(currentStatus);
        setStatusFetchedAt(Date.now());
        setError('');
        setApiHealth('online');
      } catch (caughtError) {
        if (!silent) setError(explainError(caughtError));
        setApiHealth('offline');
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [hasValidUserId, trimmedUserId]
  );

  const runAction = useCallback(
    async (label, action) => {
      if (!hasValidUserId) {
        setError('Enter a numeric user ID before using the timer.');
        return;
      }

      setBusyAction(label);
      setError('');

      try {
        await action(trimmedUserId);
        addActivity(label);
        await refreshStatus({ silent: true });
      } catch (caughtError) {
        setError(explainError(caughtError));
      } finally {
        setBusyAction(null);
      }
    },
    [addActivity, hasValidUserId, refreshStatus, trimmedUserId]
  );

  useEffect(() => {
    saveUserId(userId);
  }, [userId]);

  useEffect(() => {
    let cancelled = false;

    async function checkHealth() {
      try {
        await api.healthCheck();
        if (!cancelled) setApiHealth('online');
      } catch {
        if (!cancelled) setApiHealth('offline');
      }
    }

    checkHealth();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    refreshStatus({ silent: false });
  }, [refreshStatus]);

  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(intervalId);
  }, []);

  useEffect(() => {
    const intervalId = window.setInterval(() => refreshStatus({ silent: true }), 10000);
    return () => window.clearInterval(intervalId);
  }, [refreshStatus]);

  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div className="hero-copy">
          <p className="eyebrow">Minuteman</p>
          <h1>Track the work day without losing focus.</h1>
          <p>
            Start the day, switch between task and travel timers, and keep a live view of the time logged today.
          </p>
        </div>

        <div className="api-card">
          <div>
            <span className="label">API</span>
            <strong>{api.baseUrl}</strong>
          </div>
          <StatusPill tone={apiHealth === 'online' ? 'success' : apiHealth === 'offline' ? 'danger' : 'neutral'}>
            {apiHealth === 'online' ? 'Online' : apiHealth === 'offline' ? 'Offline' : 'Checking'}
          </StatusPill>
        </div>
      </section>

      <section className="toolbar panel">
        <label className="input-group">
          <span>User ID</span>
          <input
            inputMode="numeric"
            value={userId}
            onChange={(event) => setUserId(event.target.value)}
            placeholder="1"
          />
        </label>

        <ActionButton onClick={() => refreshStatus({ silent: false })} disabled={loading || busyAction}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </ActionButton>
      </section>

      {error ? <div className="alert" role="alert">{error}</div> : null}

      <section className="status-grid">
        <MetricCard
          label="Work day"
          value={status ? (status.workDayOpen ? 'Open' : 'Closed') : 'Unknown'}
          helper={status?.workDayOpen ? `Started ${formatClock(dayStartedAt)}` : status ? 'Start a day to begin timing.' : 'Refresh to load status.'}
        />
        <MetricCard
          label="Current timer"
          value={status ? (status.timerRunning ? status.timerType || 'Running' : 'Stopped') : 'Unknown'}
          helper={status?.timerRunning ? `Started ${formatClock(timerStartedAt)}` : status ? 'No active timer.' : 'Refresh to load status.'}
        />
        <MetricCard
          label="Running now"
          value={formatDuration(runningSeconds)}
          helper={status?.timerRunning ? 'Live estimate' : 'Timer is idle'}
        />
        <MetricCard
          label="Total today"
          value={formatDuration(totalSecondsToday)}
          helper={status?.timerRunning ? 'Closed time + live timer' : 'Closed timers only'}
        />
      </section>

      <section className="main-grid">
        <div className="panel control-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Today</p>
              <h2>{formatDate(dayStartedAt)}</h2>
            </div>
            <StatusPill tone={status?.workDayOpen ? 'success' : 'neutral'}>
              {status?.workDayOpen ? 'Day open' : 'Day closed'}
            </StatusPill>
          </div>

          <div className="action-grid">
            <ActionButton
              variant="primary"
              onClick={() => runAction('Started work day', api.startDay)}
              disabled={!canStartDay || Boolean(busyAction)}
              isLoading={busyAction === 'Started work day'}
            >
              Start day
            </ActionButton>
            <ActionButton
              variant="danger"
              onClick={() => runAction('Ended work day', api.endDay)}
              disabled={!canEndDay || Boolean(busyAction)}
              isLoading={busyAction === 'Ended work day'}
            >
              End day
            </ActionButton>
          </div>

          <div className="timer-actions">
            {TIMER_TYPES.map((timer) => (
              <button
                key={timer.value}
                className="timer-type-card"
                disabled={!canStartTimer || Boolean(busyAction)}
                onClick={() => runAction(`Started ${timer.value.toLowerCase()} timer`, (id) => api.startTimer(id, timer.value))}
              >
                <span>{timer.label}</span>
                <small>{timer.description}</small>
              </button>
            ))}
          </div>

          <ActionButton
            variant="primary"
            onClick={() => runAction('Stopped timer', api.stopTimer)}
            disabled={!canStopTimer || Boolean(busyAction)}
            isLoading={busyAction === 'Stopped timer'}
          >
            Stop timer
          </ActionButton>
        </div>

        <aside className="panel summary-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Summary</p>
              <h2>Day snapshot</h2>
            </div>
          </div>

          <dl className="summary-list">
            <div>
              <dt>Clock in</dt>
              <dd>{formatClock(dayStartedAt)}</dd>
            </div>
            <div>
              <dt>Day session</dt>
              <dd>{formatDuration(daySessionSeconds)}</dd>
            </div>
            <div>
              <dt>Timer type</dt>
              <dd>{status?.timerRunning ? status.timerType : '--'}</dd>
            </div>
            <div>
              <dt>User</dt>
              <dd>#{trimmedUserId || '--'}</dd>
            </div>
          </dl>

          <div className="activity-box">
            <h3>Recent actions</h3>
            {activityLog.length ? (
              <ul>
                {activityLog.map((item, index) => (
                  <li key={`${item.timestamp}-${index}`}>
                    <span>{item.timestamp}</span>
                    <p>{item.message}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="empty-state">Actions you take in this browser session will appear here.</p>
            )}
          </div>
        </aside>
      </section>
    </main>
  );
}
