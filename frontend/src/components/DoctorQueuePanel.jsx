import { useCallback, useEffect, useState } from 'react';
import { api, errorMessage } from '../api/client';
import { useQueueUpdates } from '../hooks/useQueueUpdates';
import { prettyDate, shortTime, toISODate } from '../utils/dates';
import { ConfirmDialog, EmptyState, ErrorBanner, Spinner, StatusBadge, SuccessBanner } from './Ui';

/** Which buttons make sense for each status (the server re-validates every transition). */
export function actionsFor(status, isAdmin) {
  const actions = [];
  if (status === 'BOOKED' && isAdmin) actions.push('check-in');
  if (status === 'CALLED') actions.push('start');
  if (status === 'CALLED' || status === 'IN_CONSULTATION') actions.push('complete');
  if (['BOOKED', 'WAITING', 'CALLED'].includes(status)) actions.push('skip');
  if (isAdmin && ['BOOKED', 'WAITING', 'CALLED'].includes(status)) actions.push('cancel');
  return actions;
}

const ACTION_LABEL = { 'check-in': 'Check in', start: 'Start', complete: 'Complete', skip: 'Skip', cancel: 'Cancel' };
const CONFIRM = {
  skip: { title: 'Skip this patient?', message: 'The patient will be marked as skipped (not available). They will leave the queue.', label: 'Skip patient' },
  cancel: { title: 'Cancel this appointment?', message: 'The appointment will be cancelled and the slot freed.', label: 'Cancel appointment' },
};

/** Queue for one doctor and day: used by the doctor dashboard and, with extra actions, by reception. */
export default function DoctorQueuePanel({ doctorId, isAdmin = false }) {
  const [date, setDate] = useState(toISODate());
  const [queue, setQueue] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(null);   // { action, entry }

  const load = useCallback(async () => {
    try {
      const { data } = await api.get(`/api/queue/doctors/${doctorId}`, { params: { date } });
      setQueue(data);
      setError('');
    } catch (err) {
      setError(errorMessage(err, 'Could not load the queue.'));
    }
  }, [doctorId, date]);

  useEffect(() => { setQueue(null); load(); }, [load]);
  const live = useQueueUpdates(doctorId, date, load);

  const run = async (request, success) => {
    setBusy(true);
    setNotice('');
    try {
      await request();
      setNotice(success);
      setError('');
      await load();
    } catch (err) {
      await load();                    // show the latest queue first (it may have changed under us)...
      setError(errorMessage(err));     // ...then the reason the action was refused
    } finally {
      setBusy(false);
    }
  };

  const callNext = () => run(
    () => api.post(`/api/queue/doctors/${doctorId}/call-next`, null, { params: { date } }),
    'Next patient called.',
  );

  const perform = (action, entry) => {
    const paths = {
      'check-in': `/api/appointments/${entry.appointmentId}/check-in`,
      cancel: `/api/appointments/${entry.appointmentId}/cancel`,
      start: `/api/queue/appointments/${entry.appointmentId}/start`,
      complete: `/api/queue/appointments/${entry.appointmentId}/complete`,
      skip: `/api/queue/appointments/${entry.appointmentId}/skip`,
    };
    return run(() => api.post(paths[action]), `${entry.token}: ${ACTION_LABEL[action].toLowerCase()} done.`);
  };

  const onAction = (action, entry) => (CONFIRM[action] ? setConfirm({ action, entry }) : perform(action, entry));

  return (
    <section aria-label="Queue">
      <div className="toolbar">
        <div className="field inline">
          <label htmlFor="queue-date">Date</label>
          <input id="queue-date" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </div>
        <span className={`live-dot ${live ? 'on' : ''}`}>{live ? 'Live' : 'Auto-refresh'}</span>
        <span className="spacer" />
        <button type="button" className="btn btn-primary" onClick={callNext} disabled={busy || !queue}>
          Call next patient
        </button>
      </div>

      <ErrorBanner message={error} onRetry={load} />
      <SuccessBanner message={notice} />
      {!queue && !error && <Spinner label="Loading queue..." />}

      {queue && (
        <>
          <div className="stat-row">
            <div className="stat"><span className="queue-label">Now serving</span><strong data-testid="doctor-now-serving">{queue.nowServing || '—'}</strong></div>
            <div className="stat"><span className="queue-label">Waiting</span><strong data-testid="doctor-waiting">{queue.waitingCount}</strong></div>
            <div className="stat"><span className="queue-label">Total today</span><strong>{queue.entries.length}</strong></div>
          </div>

          {queue.entries.length === 0 ? (
            <EmptyState title="No appointments on this day">{prettyDate(date)} has no bookings for this doctor yet.</EmptyState>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Token</th><th>Patient</th><th>Time</th><th>Status</th><th>Actions</th></tr></thead>
                <tbody>
                  {queue.entries.map((e) => (
                    <tr key={e.appointmentId}>
                      <td><strong>{e.token}</strong></td>
                      <td>{e.patientName}</td>
                      <td>{shortTime(e.slotTime)}</td>
                      <td><StatusBadge status={e.status} /></td>
                      <td className="actions">
                        {actionsFor(e.status, isAdmin).map((a) => (
                          <button key={a} type="button" disabled={busy}
                                  className={`btn btn-small ${a === 'cancel' || a === 'skip' ? 'btn-outline-danger' : 'btn-outline'}`}
                                  onClick={() => onAction(a, e)}>
                            {ACTION_LABEL[a]}
                          </button>
                        ))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <ConfirmDialog
        open={Boolean(confirm)}
        danger
        title={confirm ? `${CONFIRM[confirm.action].title} (${confirm.entry.token})` : ''}
        message={confirm ? CONFIRM[confirm.action].message : ''}
        confirmLabel={confirm ? CONFIRM[confirm.action].label : ''}
        onCancel={() => setConfirm(null)}
        onConfirm={() => { const c = confirm; setConfirm(null); perform(c.action, c.entry); }}
      />
    </section>
  );
}
