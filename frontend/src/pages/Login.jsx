import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { homePathFor, useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/Ui';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (!email.trim() || !password) {
      setError('Please enter your e-mail and password.');
      return;
    }
    setBusy(true);
    try {
      const user = await login(email.trim(), password);
      navigate(location.state?.from || homePathFor(user), { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Login failed.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="container narrow">
      <h1>Log in</h1>
      <form className="card form" onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="email">E-mail</label>
          <input id="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <ErrorBanner message={error} />
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Signing in...' : 'Log in'}</button>
        <p className="muted">New here? <Link to="/register">Create a patient account</Link></p>
      </form>
    </div>
  );
}
