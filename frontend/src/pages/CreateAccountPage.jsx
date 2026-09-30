import { useRef } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { useForm } from '../components/useForm';

/**
 * Section 7.2: name, email, and account type, plus the password and address the
 * bank needs. Submitting registers the customer, logs them in, and opens the account.
 */
export default function CreateAccountPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const inProgress = useRef(false); // stay on this page while the new account is being opened
  const form = useForm({
    name: '',
    email: '',
    password: '',
    accountType: 'SAVINGS',
    'address.street': '',
    'address.city': '',
    'address.state': '',
    'address.zip': '',
  });

  // Already logged in: opening another account has its own page
  if (user && !inProgress.current) return <Navigate to="/accounts/new" replace />;

  async function handleSubmit(event) {
    event.preventDefault();
    inProgress.current = true;

    const session = await form.submit((values) =>
      register({
        name: values.name,
        email: values.email,
        password: values.password,
        address: {
          street: values['address.street'],
          city: values['address.city'],
          state: values['address.state'],
          zip: values['address.zip'],
        },
      }),
    );
    if (!session) {
      inProgress.current = false;
      return;
    }

    try {
      const account = await accounts.create(session.user.userId, form.values.accountType);
      navigate(`/accounts/${account.accountId}`, {
        replace: true,
        state: { notice: 'Welcome to Simple Bank. Your account is open.' },
      });
    } catch (error) {
      navigate('/', {
        replace: true,
        state: { notice: `You're registered, but the account couldn't be opened: ${error.message}` },
      });
    }
  }

  const { errors, bind } = form;

  return (
    <div className="narrow wide">
      <h1>Create account</h1>
      <p className="muted">Already a customer? <Link to="/login">Log in</Link></p>
      <Alert>{form.message}</Alert>

      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label="Name" autoComplete="name" error={errors.name} {...bind('name')} />
        <FormField label="Email" type="email" autoComplete="email" error={errors.email} {...bind('email')} />
        <FormField label="Password" type="password" autoComplete="new-password"
          hint="At least 8 characters, with at least one letter and one number."
          error={errors.password} {...bind('password')} />
        <FormField label="Account type" as="select" error={errors.accountType} {...bind('accountType')}>
          <option value="SAVINGS">Savings</option>
          <option value="CHECKING">Checking</option>
        </FormField>

        <fieldset className="stack">
          <legend>Address</legend>
          <FormField label="Street" autoComplete="street-address" error={errors['address.street']}
            {...bind('address.street')} />
          <div className="row">
            <FormField label="City" autoComplete="address-level2" error={errors['address.city']}
              {...bind('address.city')} />
            <FormField label="State" autoComplete="address-level1" maxLength={2} hint="2 letters, e.g. MD"
              error={errors['address.state']} {...bind('address.state')} />
            <FormField label="ZIP code" autoComplete="postal-code" inputMode="numeric"
              error={errors['address.zip']} {...bind('address.zip')} />
          </div>
        </fieldset>

        <button type="submit" className="button" disabled={form.submitting}>
          {form.submitting ? 'Creating your account…' : 'Create account'}
        </button>
      </form>
    </div>
  );
}
