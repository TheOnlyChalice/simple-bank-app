import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useTheme } from '../theme/ThemeContext';

/** The header and navigation around every page. */
export default function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const { language, setLanguage, t } = useLanguage();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  function handleLogout() {
    setMenuOpen(false);
    logout();
    navigate('/login');
  }

  return (
    <>
      <a className="skip-link" href="#main">{t('nav.skipToContent')}</a>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand" onClick={() => setMenuOpen(false)}>{t('nav.brand')}</Link>

          <button
            type="button"
            className="icon-button nav-toggle"
            aria-expanded={menuOpen}
            aria-controls="main-nav"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span aria-hidden="true">☰</span>
            <span className="visually-hidden">{t('nav.menu')}</span>
          </button>

          <nav id="main-nav" className={`nav ${menuOpen ? 'open' : ''}`} aria-label="Main">
            {user && !isAdmin && (
              <>
                <NavLink to="/" end onClick={() => setMenuOpen(false)}>{t('nav.myAccounts')}</NavLink>
                <NavLink to="/accounts/new" onClick={() => setMenuOpen(false)}>{t('nav.openAccount')}</NavLink>
                <NavLink to="/transfer" onClick={() => setMenuOpen(false)}>{t('nav.transfer')}</NavLink>
                <NavLink to="/profile" onClick={() => setMenuOpen(false)}>{t('nav.profile')}</NavLink>
              </>
            )}
            {isAdmin && (
              <>
                <NavLink to="/admin/users" onClick={() => setMenuOpen(false)}>{t('nav.customers')}</NavLink>
                <NavLink to="/admin/accounts" onClick={() => setMenuOpen(false)}>{t('nav.accounts')}</NavLink>
                <NavLink to="/transfers/scheduled" onClick={() => setMenuOpen(false)}>{t('nav.scheduled')}</NavLink>
                <NavLink to="/admin/audit" onClick={() => setMenuOpen(false)}>{t('nav.auditLog')}</NavLink>
              </>
            )}
          </nav>

          <div className="session">
            <button
              type="button"
              className="icon-button"
              onClick={toggleTheme}
              aria-label={theme === 'dark' ? t('nav.theme.toLight') : t('nav.theme.toDark')}
              title={theme === 'dark' ? t('nav.theme.toLight') : t('nav.theme.toDark')}
            >
              {theme === 'dark' ? '☀' : '🌙'}
            </button>
            <button
              type="button"
              className="icon-button"
              onClick={() => setLanguage(language === 'es' ? 'en' : 'es')}
              aria-label={t('nav.language')}
              title={t('nav.language')}
            >
              {language === 'es' ? 'EN' : 'ES'}
            </button>

            {user ? (
              <>
                <span className="who">
                  {user.name}
                  {isAdmin && <span className="staff-tag">{t('nav.staff')}</span>}
                </span>
                <button type="button" className="button small outline-light" onClick={handleLogout}>
                  {t('nav.logOut')}
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login" className="session-link">{t('nav.logIn')}</NavLink>
                <Link to="/create-account" className="button small brass">{t('nav.createAccount')}</Link>
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

