import { act, renderHook } from '@testing-library/react';
import { useQueueUpdates } from './useQueueUpdates';

class FakeEventSource {
  static instances = [];
  constructor(url) {
    this.url = url;
    this.listeners = {};
    this.closed = false;
    FakeEventSource.instances.push(this);
  }
  addEventListener(name, fn) { this.listeners[name] = fn; }
  close() { this.closed = true; }
}

beforeEach(() => {
  FakeEventSource.instances = [];
  window.EventSource = FakeEventSource;
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  delete window.EventSource;
});

describe('useQueueUpdates', () => {
  it('subscribes to the doctor/date stream and refetches on a queue-update event', () => {
    const onChange = vi.fn();
    renderHook(() => useQueueUpdates(4, '2030-01-10', onChange));
    const source = FakeEventSource.instances[0];
    expect(source.url).toContain('/api/queue/stream?doctorId=4&date=2030-01-10');
    act(() => source.listeners['queue-update']());
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('reports live=true once the stream opens', () => {
    const { result } = renderHook(() => useQueueUpdates(4, '2030-01-10', vi.fn()));
    expect(result.current).toBe(false);
    act(() => FakeEventSource.instances[0].onopen());
    expect(result.current).toBe(true);
    act(() => FakeEventSource.instances[0].onerror());
    expect(result.current).toBe(false);
  });

  it('keeps refreshing by polling even when the stream is down', () => {
    const onChange = vi.fn();
    renderHook(() => useQueueUpdates(4, '2030-01-10', onChange, 5000));
    act(() => { vi.advanceTimersByTime(15000); });
    expect(onChange).toHaveBeenCalledTimes(3);
  });

  it('falls back to polling only when EventSource is unavailable', () => {
    delete window.EventSource;
    const onChange = vi.fn();
    renderHook(() => useQueueUpdates(4, '2030-01-10', onChange, 1000));
    act(() => { vi.advanceTimersByTime(2000); });
    expect(onChange).toHaveBeenCalledTimes(2);
  });

  it('closes the stream and stops polling on unmount', () => {
    const onChange = vi.fn();
    const { unmount } = renderHook(() => useQueueUpdates(4, '2030-01-10', onChange, 1000));
    const source = FakeEventSource.instances[0];
    unmount();
    expect(source.closed).toBe(true);
    act(() => { vi.advanceTimersByTime(5000); });
    expect(onChange).not.toHaveBeenCalled();
  });
});
