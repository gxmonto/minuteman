import { geofenceFor, handleSiteExit, type ExitDeps } from '../geofence';

function deps(overrides: Partial<ExitDeps> = {}): ExitDeps {
  return {
    loadConfig: jest.fn().mockResolvedValue({ baseUrl: 'https://srv', token: 'tok' }),
    currentPosition: jest.fn().mockResolvedValue({ latitude: 1, longitude: 2, accuracyMeters: 10 }),
    report: jest.fn().mockResolvedValue({ timerRunning: true, siteId: 1, distanceMeters: 900, insideSite: false, notified: true }),
    notifyLocally: jest.fn().mockResolvedValue(undefined),
    stopWatching: jest.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe('leaving the site', () => {
  it('reports the position and lets the server send the push', async () => {
    const d = deps();
    await handleSiteExit(d);
    expect(d.report).toHaveBeenCalledWith('https://srv', 'tok', { latitude: 1, longitude: 2, accuracyMeters: 10 });
    expect(d.notifyLocally).not.toHaveBeenCalled();
    expect(d.stopWatching).not.toHaveBeenCalled();
  });

  it('notifies locally when the server is unreachable', async () => {
    const d = deps({ report: jest.fn().mockRejectedValue(new Error('offline')) });
    await handleSiteExit(d);
    expect(d.notifyLocally).toHaveBeenCalledTimes(1);
  });

  it('notifies locally when there is no GPS fix', async () => {
    const d = deps({ currentPosition: jest.fn().mockResolvedValue(null) });
    await handleSiteExit(d);
    expect(d.report).not.toHaveBeenCalled();
    expect(d.notifyLocally).toHaveBeenCalledTimes(1);
  });

  it('stops watching once the timer is no longer running', async () => {
    const d = deps({
      report: jest.fn().mockResolvedValue({ timerRunning: false, siteId: null, distanceMeters: null, insideSite: null, notified: false }),
    });
    await handleSiteExit(d);
    expect(d.stopWatching).toHaveBeenCalled();
  });

  it('stops watching after sign-out', async () => {
    const d = deps({ loadConfig: jest.fn().mockResolvedValue({ baseUrl: null, token: null }) });
    await handleSiteExit(d);
    expect(d.stopWatching).toHaveBeenCalled();
    expect(d.report).not.toHaveBeenCalled();
  });
});

describe('geofenceFor', () => {
  const site = { id: 7, source: 'local', externalId: null, name: 'S', address: null, latitude: 19.4, longitude: -99.1, radiusMeters: 50 };

  it('watches exits only, with a minimum radius', () => {
    expect(geofenceFor(site)).toEqual({
      identifier: 'site-7',
      latitude: 19.4,
      longitude: -99.1,
      radius: 100,
      notifyOnEnter: false,
      notifyOnExit: true,
    });
  });

  it('skips sites without coordinates', () => {
    expect(geofenceFor({ ...site, latitude: null })).toBeNull();
    expect(geofenceFor(null)).toBeNull();
  });
});
