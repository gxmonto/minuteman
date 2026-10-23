import { formatDuration, formatHours, secondsSince } from '../format';

describe('format', () => {
  it('formats running timers', () => {
    expect(formatDuration(0)).toBe('0:00');
    expect(formatDuration(125)).toBe('2:05');
    expect(formatDuration(3725)).toBe('1:02:05');
    expect(formatDuration(-5)).toBe('0:00');
  });

  it('formats daily totals', () => {
    expect(formatHours(300)).toBe('5 min');
    expect(formatHours(3725)).toBe('1 h 02 min');
  });

  it('measures elapsed time with the server clock', () => {
    const start = '2026-10-23T12:00:00Z';
    const phoneNow = Date.parse('2026-10-23T12:04:00Z');
    // Phone is 1 minute behind the server.
    expect(secondsSince(start, 60_000, phoneNow)).toBe(300);
  });
});
