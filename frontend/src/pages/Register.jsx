import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { homePathFor, useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/Ui';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const validate = () => {
    const errs = {};
    if (!form.fullName.trim()) errs.fullName = 'Please enter your full name.';
    if (!/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Please enter a valid e-mail address.';
    if (form.password.length < 8) errs.password = 'Password must be at least 8 characters.';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;
    setBusy(true);
    try {
      const user = await register(form.fullName.trim(), form.email.trim(), form.password);
      navigate(homePathFor(user), { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Registration failed.'));
    } finally {
      setBusy(false);
    }
  };

  const field = (id, label, type, autoComplete, hint) => (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} type={type} autoComplete={autoComplete} value={form[id]} onChange={set(id)}
             aria-invalid={Boolean(errors[id])} aria-describedby={`${id}-msg`} />
      {errors[id] ? <p className="field-error" id={`${id}-msg`}>{errors[id]}</p> : hint && <p className="muted small" id={`${id}-msg`}>{hint}</p>}
    </div>
  );

  return (
    <div className="container narrow">
      <h1>Create your account</h1>
      <form className="card form" onSubmit={submit} noValidate>
        {field('fullName', 'Full name', 'text', 'name')}
        {field('email', 'E-mail', 'email', 'email')}
        {field('password', 'Password', 'password', 'new-password', 'At least 8 characters.')}
        <ErrorBanner message={error} />
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Creating account...' : 'Register'}</button>
        <p className="muted small">We only collect what is needed to manage your appointments. Please do not enter medical details.</p>
        <p className="muted">Already registered? <Link to="/login">Log in</Link></p>
      </form>
    </div>
  );
}
