import { Navigate, useLocation } from 'react-router-dom';
import { homePathFor, useAuth } from '../auth/AuthContext';
import { Spinner } from './Ui';

/** UI-level guard only; the backend enforces the same rules on every request. */
export default function ProtectedRoute({ roles, children }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Spinner label="Checking your session..." />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homePathFor(user)} replace />;
  return children;
}
