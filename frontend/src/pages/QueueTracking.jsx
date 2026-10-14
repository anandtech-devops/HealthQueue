import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import AppointmentQueue from '../components/AppointmentQueue';
import { ConfirmDialog, ErrorBanner, Spinner, SuccessBanner } from '../components/Ui';
import { useAppointmentActions } from './Dashboard';

/** Dedicated live tracking page for one appointment (good for keeping open on a phone). */
export default function QueueTracking() {
  const { appointmentId } = useParams();
  const location = useLocation();
  const [appointment, setAppointment] = useState(null);
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  const actions = useAppointmentActions(() => setVersion((v) => v + 1));

  useEffect(() => {
    api.get(`/api/appointments/${appointmentId}`)
      .then((r) => setAppointment(r.data))
      .catch((err) => setError(errorMessage(err, 'Appointment not found.')));
  }, [appointmentId]);

  if (error) return <div className="container"><ErrorBanner message={error} /><Link to="/dashboard">Back to my appointments</Link></div>;
  if (!appointment) return <div className="container"><Spinner label="Loading..." /></div>;

  return (
    <div className="container narrow-wide">
      <h1>Live queue tracking</h1>
      <SuccessBanner message={location.state?.justBooked ? `Booked! Your token is ${appointment.token}.` : actions.notice} />
      <ErrorBanner message={actions.error} />
      <AppointmentQueue key={version} appointment={appointment}>
        {(status) => status && (
          <>
            {status.status === 'BOOKED' && (
              <button type="button" className="btn btn-small btn-green" onClick={() => actions.checkIn(appointment.id)}>I have arrived</button>
            )}
            {['BOOKED', 'WAITING'].includes(status.status) && (
              <button type="button" className="btn btn-small btn-outline-danger" onClick={() => actions.setToCancel(appointment.id)}>Cancel appointment</button>
            )}
          </>
        )}
      </AppointmentQueue>
      <p><Link to="/dashboard">&larr; All my appointments</Link></p>
      <ConfirmDialog
        open={Boolean(actions.toCancel)} danger
        title="Cancel this appointment?"
        message="Your token will be released and you will lose your place in the queue."
        confirmLabel="Yes, cancel it"
        onCancel={() => actions.setToCancel(null)}
        onConfirm={actions.confirmCancel}
      />
    </div>
  );
}
