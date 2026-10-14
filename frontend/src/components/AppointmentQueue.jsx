import { useCallback, useEffect, useState } from 'react';
import { api, errorMessage } from '../api/client';
import { useQueueUpdates } from '../hooks/useQueueUpdates';
import QueueCard from './QueueCard';
import { ErrorBanner, Spinner } from './Ui';

/**
 * Live queue card for one appointment. Loads the patient's status, then refreshes whenever the backend
 * pushes a queue-update (SSE) and on a polling timer as a fallback.
 */
export default function AppointmentQueue({ appointment, onChanged, children }) {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState(null);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get(`/api/appointments/${appointment.id}/queue`);
      setStatus((prev) => {
        if (prev && prev.status !== data.status) onChanged?.();
        return data;
      });
      setUpdatedAt(new Date());
      setError('');
    } catch (err) {
      setError(errorMessage(err, 'Could not load your queue status.'));
    }
  }, [appointment.id, onChanged]);

  useEffect(() => { load(); }, [load]);

  const live = useQueueUpdates(appointment.doctorId, appointment.date, load);
  const terminal = status && ['COMPLETED', 'CANCELLED', 'SKIPPED'].includes(status.status);

  if (!status && !error) return <Spinner label="Loading queue status..." />;
  return (
    <div>
      <ErrorBanner message={error} onRetry={load} />
      {status && <QueueCard status={status} />}
      <div className="live-row">
        {!terminal && (
          <span className={`live-dot ${live ? 'on' : ''}`} title={live ? 'Live updates connected' : 'Refreshing every few seconds'}>
            {live ? 'Live' : 'Auto-refresh'}
          </span>
        )}
        {updatedAt && <span className="muted small">Updated {updatedAt.toLocaleTimeString()}</span>}
        <span className="spacer" />
        {children && children(status, load)}
      </div>
    </div>
  );
}
