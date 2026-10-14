import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '../api/client';
import BookingForm from './BookingForm';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, api: { get: vi.fn(), post: vi.fn() } };
});

const doctor = { id: 7, fullName: 'Dr. Demo' };
const slots = [
  { time: '09:00:00', available: true },
  { time: '09:15:00', available: false },
  { time: '09:30:00', available: true },
];

beforeEach(() => {
  vi.clearAllMocks();
  api.get.mockResolvedValue({ data: slots });
});

describe('BookingForm', () => {
  it('lists slots and disables the ones that are taken', async () => {
    render(<BookingForm doctor={doctor} onBooked={vi.fn()} />);
    const taken = await screen.findByLabelText('09:15');
    expect(taken).toBeDisabled();
    expect(screen.getByLabelText('09:00')).toBeEnabled();
  });

  it('validates that a slot is selected before calling the API', async () => {
    render(<BookingForm doctor={doctor} onBooked={vi.fn()} />);
    await screen.findByLabelText('09:00');
    await userEvent.click(screen.getByRole('button', { name: /confirm appointment/i }));
    expect(await screen.findByText(/please select a time slot/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('books the selected slot and reports the new appointment', async () => {
    const onBooked = vi.fn();
    api.post.mockResolvedValue({ data: { id: 99, token: 'A-001' } });
    render(<BookingForm doctor={doctor} onBooked={onBooked} />);
    await userEvent.click(await screen.findByLabelText('09:30'));
    await userEvent.click(screen.getByRole('button', { name: /confirm appointment/i }));
    await waitFor(() => expect(onBooked).toHaveBeenCalledWith({ id: 99, token: 'A-001' }));
    expect(api.post).toHaveBeenCalledWith('/api/appointments', expect.objectContaining({ doctorId: 7, slotTime: '09:30:00' }));
  });

  it('shows the server message when the slot was just taken (409) and reloads the slots', async () => {
    api.post.mockRejectedValue({ response: { status: 409, data: { message: 'That slot was just taken' } } });
    render(<BookingForm doctor={doctor} onBooked={vi.fn()} />);
    await userEvent.click(await screen.findByLabelText('09:00'));
    await userEvent.click(screen.getByRole('button', { name: /confirm appointment/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('That slot was just taken');
    await waitFor(() => expect(api.get.mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it('shows a friendly message when the server is unreachable', async () => {
    api.post.mockRejectedValue({ code: 'ERR_NETWORK' });
    render(<BookingForm doctor={doctor} onBooked={vi.fn()} />);
    await userEvent.click(await screen.findByLabelText('09:00'));
    await userEvent.click(screen.getByRole('button', { name: /confirm appointment/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the server/i);
  });
});
