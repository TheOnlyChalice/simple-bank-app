import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';

/** The header and navigation around every page. */
export default function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">Simple Bank</Link>

          <nav className="nav" aria-label="Main">
            {user && !isAdmin && (
              <>
                <NavLink to="/" end>My accounts</NavLink>
                <NavLink to="/accounts/new">Open an account</NavLink>
                <NavLink to="/transfer">Transfer</NavLink>
                <NavLink to="/profile">Profile</NavLink>
              </>
            )}
            {isAdmin && (
              <>
                <NavLink to="/admin/users">Customers</NavLink>
                <NavLink to="/admin/accounts">Accounts</NavLink>
                <NavLink to="/admin/audit">Audit log</NavLink>
              </>
            )}
          </nav>

          <div className="session">
            {user ? (
              <>
                <span className="who">
                  {user.name}
                  {isAdmin && <span className="staff-tag">Staff</span>}
                </span>
                <button type="button" className="button small outline-light" onClick={handleLogout}>
                  Log out
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login" className="session-link">Log in</NavLink>
                <Link to="/create-account" className="button small brass">Create account</Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="main" className="page">
        <Outlet />
      </main>
    </>
  );
}
