import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { api } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import Login from './Login';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, api: { get: vi.fn(), post: vi.fn() } };
});

function setup() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/dashboard" element={<h1>Patient area</h1>} />
          <Route path="/doctor" element={<h1>Doctor area</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('Login', () => {
  it('requires e-mail and password', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/enter your e-mail and password/i);
    expect(api.post).not.toHaveBeenCalled();
  });

  it('shows the API error for wrong credentials', async () => {
    api.post.mockRejectedValue({ response: { status: 401, data: { message: 'Invalid e-mail or password' } } });
    setup();
    await userEvent.type(screen.getByLabelText('E-mail'), 'a@b.example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid e-mail or password');
  });

  it('stores the token and sends each role to its own dashboard', async () => {
    api.post.mockResolvedValue({ data: { token: 'jwt-123', user: { id: 1, fullName: 'Dr X', role: 'DOCTOR' } } });
    setup();
    await userEvent.type(screen.getByLabelText('E-mail'), 'doc@demo.example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'some-password');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    expect(await screen.findByText('Doctor area')).toBeInTheDocument();
    expect(localStorage.getItem('hq_token')).toBe('jwt-123');
  });
});
