import { useRef } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
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
  const { t } = useLanguage();
  const { showToast } = useToast();
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
      showToast(t('createAccount.welcomeNotice'));
      navigate(`/accounts/${account.accountId}`, { replace: true });
    } catch (error) {
      showToast(t('createAccount.failedNotice', { message: error.message }), 'error');
      navigate('/', { replace: true });
    }
  }

  const { errors, bind } = form;

  return (
    <div className="narrow wide">
      <h1>{t('createAccount.title')}</h1>
      <p className="muted">{t('createAccount.alreadyCustomer')} <Link to="/login">{t('createAccount.logIn')}</Link></p>
      <Alert>{form.message}</Alert>

      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label={t('createAccount.name')} autoComplete="name" error={errors.name} {...bind('name')} />
        <FormField label={t('createAccount.email')} type="email" autoComplete="email" error={errors.email} {...bind('email')} />
        <FormField label={t('createAccount.password')} type="password" autoComplete="new-password"
          hint={t('createAccount.passwordHint')}
          error={errors.password} {...bind('password')} />
        <FormField label={t('createAccount.accountType')} as="select" error={errors.accountType} {...bind('accountType')}>
          <option value="SAVINGS">{t('createAccount.savings')}</option>
          <option value="CHECKING">{t('createAccount.checking')}</option>
        </FormField>

        <fieldset className="stack">
          <legend>{t('createAccount.address')}</legend>
          <FormField label={t('createAccount.street')} autoComplete="street-address" error={errors['address.street']}
            {...bind('address.street')} />
          <div className="row">
            <FormField label={t('createAccount.city')} autoComplete="address-level2" error={errors['address.city']}
              {...bind('address.city')} />
            <FormField label={t('createAccount.state')} autoComplete="address-level1" maxLength={2} hint={t('createAccount.stateHint')}
              error={errors['address.state']} {...bind('address.state')} />
            <FormField label={t('createAccount.zip')} autoComplete="postal-code" inputMode="numeric"
              error={errors['address.zip']} {...bind('address.zip')} />
          </div>
        </fieldset>

        <button type="submit" className="button" disabled={form.submitting}>
          {form.submitting ? t('createAccount.submitting') : t('createAccount.submit')}
        </button>
      </form>
    </div>
  );
}

