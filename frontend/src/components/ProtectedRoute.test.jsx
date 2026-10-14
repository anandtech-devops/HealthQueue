import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { api } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import ProtectedRoute from './ProtectedRoute';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, api: { get: vi.fn(), post: vi.fn() } };
});

function setup(path = '/admin') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<h1>Login page</h1>} />
          <Route path="/dashboard" element={<h1>Patient dashboard</h1>} />
          <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><h1>Secret admin</h1></ProtectedRoute>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ProtectedRoute', () => {
  it('redirects anonymous visitors to the login page', async () => {
    setup();
    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });

  it('sends a patient away from an admin-only page', async () => {
    localStorage.setItem('hq_token', 'jwt');
    api.get.mockResolvedValue({ data: { id: 5, fullName: 'Pat', role: 'PATIENT' } });
    setup();
    expect(await screen.findByText('Patient dashboard')).toBeInTheDocument();
    expect(screen.queryByText('Secret admin')).not.toBeInTheDocument();
  });

  it('lets an admin in', async () => {
    localStorage.setItem('hq_token', 'jwt');
    api.get.mockResolvedValue({ data: { id: 1, fullName: 'Ad', role: 'ADMIN' } });
    setup();
    expect(await screen.findByText('Secret admin')).toBeInTheDocument();
  });
});
