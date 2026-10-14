import { useEffect, useRef, useState } from 'react';
import { API_BASE } from '../api/client';

/**
 * Keeps a view fresh without manual refreshes.
 *  1. Server-Sent Events: the backend pushes a "queue-update" signal the moment the queue changes.
 *  2. Polling fallback: also refreshes every `pollMs` in case SSE is blocked or the connection drops.
 * The signal carries no data; `onChange` is expected to re-fetch through the normal authorised API.
 * Returns `live` (true while the SSE connection is open) for a small status indicator.
 */
export function useQueueUpdates(doctorId, date, onChange, pollMs = 15000) {
  const callback = useRef(onChange);
  callback.current = onChange;
  const [live, setLive] = useState(false);

  useEffect(() => {
    if (!doctorId || !date) return undefined;
    let source;
    if (typeof window !== 'undefined' && 'EventSource' in window) {
      source = new window.EventSource(`${API_BASE}/api/queue/stream?doctorId=${doctorId}&date=${date}`);
      source.addEventListener('queue-update', () => callback.current());
      source.onopen = () => setLive(true);
      source.onerror = () => setLive(false);   // EventSource retries by itself; polling covers the gap
    }
    const timer = setInterval(() => callback.current(), pollMs);
    return () => {
      clearInterval(timer);
      if (source) source.close();
      setLive(false);
    };
  }, [doctorId, date, pollMs]);

  return live;
}
