import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AppointmentQueue from '../components/AppointmentQueue';
import { ConfirmDialog, EmptyState, ErrorBanner, Spinner, StatusBadge, SuccessBanner } from '../components/Ui';
import { prettyDate, shortTime, toISODate } from '../utils/dates';

const OPEN = ['BOOKED', 'WAITING', 'CALLED', 'IN_CONSULTATION'];

export function useAppointmentActions(refresh) {
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [toCancel, setToCancel] = useState(null);

  const checkIn = async (id) => {
    setError('');
    try {
      await api.post(`/api/appointments/${id}/check-in`);
      setNotice('You are checked in. Please wait for your token to be called.');
      refresh();
    } catch (err) { setError(errorMessage(err)); }
  };

  const confirmCancel = async () => {
    const id = toCancel;
    setToCancel(null);
    setError('');
    try {
      await api.post(`/api/appointments/${id}/cancel`);
      setNotice('Your appointment was cancelled.');
      refresh();
    } catch (err) { setError(errorMessage(err)); }
  };

  return { notice, error, toCancel, setToCancel, checkIn, confirmCancel };
}

/** Patient dashboard: the live queue card is the first (and biggest) thing on the page. */
export default function Dashboard() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    api.get('/api/appointments/my', { params: { page: 0, size: 30 } })
      .then((r) => { setData(r.data); setError(''); })
      .catch((err) => setError(errorMessage(err, 'Could not load your appointments.')));
  }, []);
  useEffect(load, [load]);

  const actions = useAppointmentActions(load);
  const today = toISODate();
  const upcoming = (data?.content || []).filter((a) => OPEN.includes(a.status) && a.date >= today).reverse();
  const history = (data?.content || []).filter((a) => !upcoming.includes(a));

  return (
    <div className="container">
      <h1>Hello, {user.fullName}</h1>
      <ErrorBanner message={error || actions.error} onRetry={error ? load : undefined} />
      <SuccessBanner message={actions.notice} />
      {!data && !error && <Spinner label="Loading your appointments..." />}

      {data && data.content.length === 0 && (
        <EmptyState title="No appointments yet">
          Find a doctor and book your first appointment. <Link to="/doctors">Browse doctors</Link>
        </EmptyState>
      )}

      {upcoming.map((a) => (
        <div key={a.id} className="appointment-block">
          <AppointmentQueue appointment={a} onChanged={load}>
            {(status) => status && (
              <>
                <Link className="btn btn-small btn-outline" to={`/queue/${a.id}`}>Full screen</Link>
                {status.status === 'BOOKED' && (
                  <button type="button" className="btn btn-small btn-green" onClick={() => actions.checkIn(a.id)}>I have arrived</button>
                )}
                {['BOOKED', 'WAITING'].includes(status.status) && (
                  <button type="button" className="btn btn-small btn-outline-danger" onClick={() => actions.setToCancel(a.id)}>Cancel appointment</button>
                )}
              </>
            )}
          </AppointmentQueue>
        </div>
      ))}

      {data && upcoming.length === 0 && data.content.length > 0 && (
        <EmptyState title="No upcoming appointments">
          <Link to="/doctors">Book another appointment</Link>
        </EmptyState>
      )}

      {history.length > 0 && (
        <>
          <h2>Past and closed appointments</h2>
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Date</th><th>Time</th><th>Doctor</th><th>Token</th><th>Status</th></tr></thead>
              <tbody>
                {history.map((a) => (
                  <tr key={a.id}>
                    <td>{prettyDate(a.date)}</td><td>{shortTime(a.slotTime)}</td>
                    <td>{a.doctorName}<div className="muted small">{a.doctorTitle}</div></td>
                    <td><strong>{a.token}</strong></td><td><StatusBadge status={a.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <ConfirmDialog
        open={Boolean(actions.toCancel)} danger
        title="Cancel this appointment?"
        message="Your token will be released and you will lose your place in the queue."
        confirmLabel="Yes, cancel it"
        onCancel={() => actions.setToCancel(null)} onConfirm={actions.confirmCancel}
      />
    </div>
  );
}
