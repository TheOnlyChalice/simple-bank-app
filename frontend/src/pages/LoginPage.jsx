import { Link, Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { useForm } from '../components/useForm';

export default function LoginPage() {
  const { user, login, sessionExpired } = useAuth();
  const { t } = useLanguage();
  const location = useLocation();
  const form = useForm({ email: '', password: '' });

  // Once logged in, go back to the page that asked for a login (or home)
  if (user) return <Navigate to={location.state?.from ?? '/'} replace />;

  async function handleSubmit(event) {
    event.preventDefault();
    await form.submit((values) => login(values.email, values.password));
  }

  return (
    <div className="narrow">
      <h1>{t('login.title')}</h1>
      {sessionExpired && <Alert kind="info">{t('login.sessionExpired')}</Alert>}
      <Alert>{form.message}</Alert>

      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label={t('login.email')} type="email" autoComplete="email" error={form.errors.email} {...form.bind('email')} />
        <FormField label={t('login.password')} type="password" autoComplete="current-password"
          error={form.errors.password} {...form.bind('password')} />
        <button type="submit" className="button" disabled={form.submitting}>
          {form.submitting ? t('login.submitting') : t('login.submit')}
        </button>
      </form>

      <p className="muted">{t('login.newHere')} <Link to="/create-account">{t('login.createAccount')}</Link></p>
    </div>
  );
}

