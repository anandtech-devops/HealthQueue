import { useEffect, useState } from 'react';
import { api, errorMessage } from '../api/client';
import { addDays, shortTime, toISODate } from '../utils/dates';
import { ErrorBanner, Spinner } from './Ui';

const MAX_DAYS_AHEAD = 30;

/** Date + slot picker. Validation happens here for a quick response and again on the server. */
export default function BookingForm({ doctor, onBooked }) {
  const today = toISODate();
  const [date, setDate] = useState(addDays(today, 1));
  const [slots, setSlots] = useState([]);
  const [slotTime, setSlotTime] = useState('');
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldError, setFieldError] = useState({});

  useEffect(() => {
    if (!date || date < today || date > addDays(today, MAX_DAYS_AHEAD)) {
      setSlots([]);
      return undefined;
    }
    let cancelled = false;
    setLoadingSlots(true);
    setSlotTime('');
    setError('');
    api.get(`/api/doctors/${doctor.id}/slots`, { params: { date } })
      .then((res) => !cancelled && setSlots(res.data))
      .catch((err) => !cancelled && setError(errorMessage(err, 'Could not load available times.')))
      .finally(() => !cancelled && setLoadingSlots(false));
    return () => { cancelled = true; };
  }, [doctor.id, date, today]);

  const validate = () => {
    const errs = {};
    if (!date) errs.date = 'Please choose a date.';
    else if (date < today) errs.date = 'The date cannot be in the past.';
    else if (date > addDays(today, MAX_DAYS_AHEAD)) errs.date = `Appointments can be booked up to ${MAX_DAYS_AHEAD} days ahead.`;
    if (!slotTime) errs.slot = 'Please select a time slot.';
    setFieldError(errs);
    return Object.keys(errs).length === 0;
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;
    setSubmitting(true);
    try {
      const { data } = await api.post('/api/appointments', { doctorId: doctor.id, date, slotTime });
      onBooked(data);
    } catch (err) {
      setError(errorMessage(err, 'Booking failed.'));
      // The slot may have just been taken by someone else: refresh the list.
      if (err.response?.status === 409) {
        api.get(`/api/doctors/${doctor.id}/slots`, { params: { date } }).then((res) => setSlots(res.data)).catch(() => {});
        setSlotTime('');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="card form" aria-label="Book appointment">
      <div className="field">
        <label htmlFor="date">Appointment date</label>
        <input id="date" type="date" value={date} min={today} max={addDays(today, MAX_DAYS_AHEAD)}
               onChange={(e) => setDate(e.target.value)} aria-invalid={Boolean(fieldError.date)} aria-describedby="date-error" />
        {fieldError.date && <p className="field-error" id="date-error">{fieldError.date}</p>}
      </div>

      <fieldset className="field">
        <legend>Available times</legend>
        {loadingSlots && <Spinner label="Loading available times..." />}
        {!loadingSlots && slots.length === 0 && date >= today && !error && (
          <p className="muted">No appointments are offered on this date. Please try another day.</p>
        )}
        <div className="slot-grid" role="radiogroup" aria-label="Available times">
          {slots.map((s) => (
            <label key={s.time} className={`slot ${slotTime === s.time ? 'selected' : ''} ${s.available ? '' : 'disabled'}`}>
              <input type="radio" name="slot" value={s.time} disabled={!s.available}
                     checked={slotTime === s.time} onChange={() => setSlotTime(s.time)} />
              {shortTime(s.time)}
            </label>
          ))}
        </div>
        {fieldError.slot && <p className="field-error">{fieldError.slot}</p>}
      </fieldset>

      <ErrorBanner message={error} />
      <button type="submit" className="btn btn-primary" disabled={submitting}>
        {submitting ? 'Booking...' : 'Confirm appointment'}
      </button>
      <p className="muted small">You will get a queue token as soon as the booking is confirmed.</p>
    </form>
  );
}
