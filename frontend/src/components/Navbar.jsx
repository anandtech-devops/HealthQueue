import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);

  const handleLogout = () => {
    logout();
    close();
    navigate('/');
  };

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Link to="/" className="brand" onClick={close}><span className="brand-mark" aria-hidden="true">+</span> HealthQueue</Link>
        <button type="button" className="nav-toggle" aria-label="Toggle navigation" aria-expanded={open} onClick={() => setOpen(!open)}>
          &#9776;
        </button>
        <nav className={`nav-links ${open ? 'open' : ''}`} aria-label="Main">
          <NavLink to="/doctors" onClick={close}>Doctors</NavLink>
          {user?.role === 'PATIENT' && <NavLink to="/dashboard" onClick={close}>My appointments</NavLink>}
          {user?.role === 'DOCTOR' && <NavLink to="/doctor" onClick={close}>Doctor dashboard</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/admin" onClick={close}>Reception / Admin</NavLink>}
          {user ? (
            <>
              <span className="nav-user" title={user.email}>{user.fullName}</span>
              <button type="button" className="btn btn-small btn-outline" onClick={handleLogout}>Log out</button>
            </>
          ) : (
            <>
              <NavLink to="/login" onClick={close}>Log in</NavLink>
              <Link to="/register" className="btn btn-small btn-primary" onClick={close}>Register</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
