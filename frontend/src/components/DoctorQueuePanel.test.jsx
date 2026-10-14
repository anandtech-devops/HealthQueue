import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '../api/client';
import DoctorQueuePanel, { actionsFor } from './DoctorQueuePanel';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, api: { get: vi.fn(), post: vi.fn() }, API_BASE: '' };
});

const queue = {
  doctorId: 1,
  nowServing: 'A-001',
  waitingCount: 2,
  entries: [
    { appointmentId: 11, token: 'A-001', tokenNumber: 1, patientName: 'Pat One', slotTime: '09:00:00', status: 'CALLED' },
    { appointmentId: 12, token: 'A-002', tokenNumber: 2, patientName: 'Pat Two', slotTime: '09:15:00', status: 'WAITING' },
    { appointmentId: 13, token: 'A-003', tokenNumber: 3, patientName: 'Pat Three', slotTime: '09:30:00', status: 'CANCELLED' },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  api.get.mockResolvedValue({ data: queue });
  api.post.mockResolvedValue({ data: {} });
});

describe('actionsFor', () => {
  it('offers sensible actions per status', () => {
    expect(actionsFor('CALLED', false)).toEqual(['start', 'complete', 'skip']);
    expect(actionsFor('IN_CONSULTATION', false)).toEqual(['complete']);
    expect(actionsFor('WAITING', false)).toEqual(['skip']);
    expect(actionsFor('COMPLETED', true)).toEqual([]);
    expect(actionsFor('CANCELLED', true)).toEqual([]);
  });

  it('gives reception extra check-in and cancel actions', () => {
    expect(actionsFor('BOOKED', true)).toEqual(['check-in', 'skip', 'cancel']);
    expect(actionsFor('BOOKED', false)).toEqual(['skip']);
  });
});

describe('DoctorQueuePanel', () => {
  it('shows who is being served and the waiting count', async () => {
    render(<DoctorQueuePanel doctorId={1} />);
    expect(await screen.findByTestId('doctor-now-serving')).toHaveTextContent('A-001');
    expect(screen.getByTestId('doctor-waiting')).toHaveTextContent('2');
    expect(screen.getByText('Pat Two')).toBeInTheDocument();
  });

  it('calls the next patient through the API', async () => {
    render(<DoctorQueuePanel doctorId={1} />);
    await screen.findByText('Pat One');
    await userEvent.click(screen.getByRole('button', { name: /call next patient/i }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/api/queue/doctors/1/call-next', null, expect.objectContaining({ params: expect.any(Object) })));
    expect(await screen.findByText(/next patient called/i)).toBeInTheDocument();
  });

  it('asks for confirmation before skipping a patient', async () => {
    render(<DoctorQueuePanel doctorId={1} />);
    const row = (await screen.findByText('Pat Two')).closest('tr');
    await userEvent.click(within(row).getByRole('button', { name: 'Skip' }));
    expect(screen.getByRole('dialog')).toHaveTextContent(/skip this patient/i);
    expect(api.post).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: /skip patient/i }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/queue/appointments/12/skip'));
  });

  it('shows the server error when the transition is refused', async () => {
    api.post.mockRejectedValue({ response: { status: 409, data: { message: 'Complete or skip the patient who is currently called first' } } });
    render(<DoctorQueuePanel doctorId={1} />);
    await screen.findByText('Pat One');
    await userEvent.click(screen.getByRole('button', { name: /call next patient/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/currently called first/i);
  });

  it('shows an empty state when nobody has booked', async () => {
    api.get.mockResolvedValue({ data: { ...queue, entries: [], waitingCount: 0, nowServing: null } });
    render(<DoctorQueuePanel doctorId={1} />);
    expect(await screen.findByText(/no appointments on this day/i)).toBeInTheDocument();
  });
});
