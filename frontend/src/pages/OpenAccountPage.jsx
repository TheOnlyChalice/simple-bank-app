import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { useForm } from '../components/useForm';

/** For customers who are already registered: open another savings or checking account. */
export default function OpenAccountPage() {
  const { user } = useAuth();
  const { t } = useLanguage();
  const navigate = useNavigate();
  const form = useForm({ accountType: 'SAVINGS' });

  async function handleSubmit(event) {
    event.preventDefault();
    const account = await form.submit((values) => accounts.create(user.userId, values.accountType));
    if (account) {
      navigate(`/accounts/${account.accountId}`, { state: { notice: t('openAccount.notice') } });
    }
  }

  return (
    <div className="narrow">
      <h1>{t('openAccount.title')}</h1>
      <Alert>{form.message}</Alert>
      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label={t('openAccount.accountType')} as="select" error={form.errors.accountType} {...form.bind('accountType')}>
          <option value="SAVINGS">{t('createAccount.savings')}</option>
          <option value="CHECKING">{t('createAccount.checking')}</option>
        </FormField>
        <div className="button-row">
          <button type="submit" className="button" disabled={form.submitting}>
            {form.submitting ? t('openAccount.submitting') : t('openAccount.submit')}
          </button>
          <Link to="/" className="button secondary">{t('openAccount.cancel')}</Link>
        </div>
      </form>
    </div>
  );
}

