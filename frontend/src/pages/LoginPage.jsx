import { Link, Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { useForm } from '../components/useForm';

export default function LoginPage() {
  const { user, login, sessionExpired } = useAuth();
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
      <h1>Log in</h1>
      {sessionExpired && <Alert kind="info">Your session ended. Log in again to continue.</Alert>}
      <Alert>{form.message}</Alert>

      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label="Email" type="email" autoComplete="email" error={form.errors.email} {...form.bind('email')} />
        <FormField label="Password" type="password" autoComplete="current-password"
          error={form.errors.password} {...form.bind('password')} />
        <button type="submit" className="button" disabled={form.submitting}>
          {form.submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      <p className="muted">New to Simple Bank? <Link to="/create-account">Create an account</Link></p>
    </div>
  );
}
