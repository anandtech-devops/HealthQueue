import { render, screen } from '@testing-library/react';
import QueueCard from './QueueCard';

const base = {
  appointmentId: 1,
  patientName: 'Demo Patient',
  doctorName: 'Dr. Demo',
  doctorTitle: 'General Physician',
  date: '2030-01-10',
  slotTime: '09:30:00',
  yourToken: 'A-012',
  nowServing: 'A-008',
  patientsAhead: 3,
  estimatedWaitMinutes: 15,
  avgConsultationMinutes: 5,
  status: 'WAITING',
};

describe('QueueCard', () => {
  it('shows token, now serving, patients ahead, estimated wait and status prominently', () => {
    render(<QueueCard status={base} />);
    expect(screen.getByTestId('your-token')).toHaveTextContent('A-012');
    expect(screen.getByTestId('now-serving')).toHaveTextContent('A-008');
    expect(screen.getByTestId('patients-ahead')).toHaveTextContent('3');
    expect(screen.getByTestId('estimated-wait')).toHaveTextContent('~15 min');
    expect(screen.getAllByText('Waiting').length).toBeGreaterThan(0);
    expect(screen.getByText('Demo Patient', { exact: false })).toBeInTheDocument();
  });

  it('labels the wait time as an estimate, not a promise', () => {
    render(<QueueCard status={base} />);
    expect(screen.getByTestId('estimate-note')).toHaveTextContent(/estimate only/i);
  });

  it('shows a dash when nobody is being served yet', () => {
    render(<QueueCard status={{ ...base, nowServing: undefined }} />);
    expect(screen.getByTestId('now-serving')).toHaveTextContent('—');
  });

  it('tells the patient when it is their turn', () => {
    render(<QueueCard status={{ ...base, status: 'CALLED', patientsAhead: 0, estimatedWaitMinutes: 0 }} />);
    expect(screen.getByText(/it's your turn/i)).toBeInTheDocument();
    expect(screen.getByTestId('patients-ahead')).toHaveTextContent('0');
  });

  it('hides wait numbers once the appointment is closed', () => {
    render(<QueueCard status={{ ...base, status: 'CANCELLED' }} />);
    expect(screen.getByTestId('patients-ahead')).toHaveTextContent('—');
    expect(screen.getByTestId('estimated-wait')).toHaveTextContent('—');
    expect(screen.queryByTestId('estimate-note')).not.toBeInTheDocument();
    expect(screen.getByText(/was cancelled/i)).toBeInTheDocument();
  });
});
