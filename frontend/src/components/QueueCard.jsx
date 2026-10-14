import { StatusBadge } from './Ui';
import { prettyDate, shortTime } from '../utils/dates';

const MESSAGES = {
  BOOKED: 'You are booked. Tap "I have arrived" when you reach the clinic.',
  WAITING: 'Please wait. This page updates automatically as the queue moves.',
  CALLED: "It's your turn. Please go to the doctor now.",
  IN_CONSULTATION: 'Your consultation is in progress.',
  COMPLETED: 'This consultation is complete. Thank you.',
  CANCELLED: 'This appointment was cancelled.',
  SKIPPED: 'You were skipped because you were not available when called. Please contact the reception desk.',
};

/** The most prominent element of the patient experience: token, now serving, patients ahead, wait, status. */
export default function QueueCard({ status }) {
  const closed = ['COMPLETED', 'CANCELLED', 'SKIPPED'].includes(status.status);
  return (
    <section className={`queue-card status-${status.status.toLowerCase()}`} aria-label="Your queue status">
      <header className="queue-card-head">
        <div>
          <strong>{status.doctorName}</strong> <span className="muted">- {status.doctorTitle}</span>
          <div className="muted">{prettyDate(status.date)} at {shortTime(status.slotTime)} &middot; {status.patientName}</div>
        </div>
        <StatusBadge status={status.status} />
      </header>

      <div className="queue-grid" aria-live="polite">
        <div className="queue-item queue-token">
          <span className="queue-label">Your token</span>
          <span className="queue-value" data-testid="your-token">{status.yourToken}</span>
        </div>
        <div className="queue-item">
          <span className="queue-label">Now serving</span>
          <span className="queue-value" data-testid="now-serving">{status.nowServing || '—'}</span>
        </div>
        <div className="queue-item">
          <span className="queue-label">Patients ahead</span>
          <span className="queue-value" data-testid="patients-ahead">{closed ? '—' : status.patientsAhead}</span>
        </div>
        <div className="queue-item">
          <span className="queue-label">Estimated wait</span>
          <span className="queue-value" data-testid="estimated-wait">
            {closed ? '—' : `~${status.estimatedWaitMinutes} min`}
          </span>
        </div>
      </div>

      <p className="queue-message">{MESSAGES[status.status]}</p>
      {!closed && (
        <p className="muted small" data-testid="estimate-note">
          Estimate only (about {status.avgConsultationMinutes} min per patient). Actual waiting time may vary.
        </p>
      )}
    </section>
  );
}
