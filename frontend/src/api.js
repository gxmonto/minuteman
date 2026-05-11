const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || '';
  const isJson = contentType.includes('application/json');
  const body = isJson ? await response.json().catch(() => null) : await response.text().catch(() => '');

  if (!response.ok) {
    const message =
      body?.message ||
      body?.error ||
      body?.title ||
      (typeof body === 'string' && body.trim()) ||
      `Request failed with status ${response.status}`;

    const error = new Error(message);
    error.status = response.status;
    error.body = body;
    throw error;
  }

  return body;
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Accept: 'application/json',
      ...(options.headers || {})
    },
    ...options
  });

  return parseResponse(response);
}

export const api = {
  baseUrl: API_BASE_URL,

  healthCheck() {
    return request('/test', { headers: { Accept: 'text/plain' } });
  },

  getCurrentStatus(userId) {
    return request(`/api/work/current/${encodeURIComponent(userId)}`);
  },

  startDay(userId) {
    return request(`/api/work/start-day/${encodeURIComponent(userId)}`, {
      method: 'POST'
    });
  },

  endDay(userId) {
    return request(`/api/work/end-day/${encodeURIComponent(userId)}`, {
      method: 'POST'
    });
  },

  startTimer(userId, type) {
    const params = new URLSearchParams({ type });
    return request(`/api/work/start-timer/${encodeURIComponent(userId)}?${params.toString()}`, {
      method: 'POST'
    });
  },

  stopTimer(userId) {
    return request(`/api/work/stop-timer/${encodeURIComponent(userId)}`, {
      method: 'POST'
    });
  }
};
