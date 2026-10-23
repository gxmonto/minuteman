import { ApiError, createApi, errorMessage, normalizeServerUrl } from '../api';

function respond(status: number, body?: unknown) {
  return Promise.resolve(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
}

describe('api client', () => {
  const fetchMock = jest.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    globalThis.fetch = fetchMock as unknown as typeof fetch;
  });

  it('sends the bearer token and JSON body', async () => {
    fetchMock.mockReturnValueOnce(respond(200, { id: 1, type: 'TRAVEL' }));
    const api = createApi({ baseUrl: 'https://srv', token: 'tok' });

    await api.startTimer({ type: 'TRAVEL', location: null });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://srv/api/work/timers/start');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(JSON.parse(init.body)).toEqual({ type: 'TRAVEL', location: null });
  });

  it('turns problem responses into ApiError with the backend code', async () => {
    fetchMock.mockReturnValueOnce(respond(409, { detail: 'Stop the running timer first', code: 'TIMER_ALREADY_RUNNING' }));
    const api = createApi({ baseUrl: 'https://srv', token: 'tok' });

    const error = await api.startTimer({ type: 'TRAVEL' }).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('TIMER_ALREADY_RUNNING');
    expect(errorMessage(error)).toBe('Ya tienes un timer corriendo. Detenlo primero.');
  });

  it('signals an expired session', async () => {
    fetchMock.mockReturnValueOnce(respond(401));
    const onUnauthorized = jest.fn();
    const api = createApi({ baseUrl: 'https://srv', token: 'old', onUnauthorized });

    await expect(api.currentStatus()).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalled();
  });

  it('reports network failures distinctly', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Network request failed'));
    const error = await createApi({ baseUrl: 'https://srv', token: null }).login('a', 'b').catch((e) => e);
    expect(error.isNetworkError).toBe(true);
    expect(errorMessage(error)).toMatch(/conexión/);
  });

  it('handles 204 responses', async () => {
    fetchMock.mockReturnValueOnce(Promise.resolve(new Response(null, { status: 204 })));
    await expect(createApi({ baseUrl: 'https://srv', token: 't' }).registerDevice('x', 'ios')).resolves.toBeUndefined();
  });

  it('normalizes server URLs', () => {
    expect(normalizeServerUrl(' minuteman.example.com/ ')).toBe('https://minuteman.example.com');
    expect(normalizeServerUrl('http://192.168.1.10:8080')).toBe('http://192.168.1.10:8080');
  });
});
