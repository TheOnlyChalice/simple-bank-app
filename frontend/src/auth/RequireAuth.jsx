import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';

/**
 * Wraps pages that need a login. Logged-out visitors go to the login page, which
 * sends them back here afterwards. With staffOnly, customers go to their home page.
 * (The backend enforces the same rules; this just avoids showing pages that would fail.)
 */
export default function RequireAuth({ staffOnly = false }) {
  const { user, isAdmin } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (staffOnly && !isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
