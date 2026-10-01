import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import FeatureIcon from '../components/FeatureIcon';
import FormField from '../components/FormField';
import { useForm } from '../components/useForm';

/** For customers who are already registered: open another savings or checking account. */
export default function OpenAccountPage() {
  const { user } = useAuth();
  const { t } = useLanguage();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const form = useForm({ accountType: 'SAVINGS' });

  async function handleSubmit(event) {
    event.preventDefault();
    const account = await form.submit((values) => accounts.create(user.userId, values.accountType));
    if (account) {
      showToast(t('openAccount.notice'));
      navigate(`/accounts/${account.accountId}`);
    }
  }

  return (
    <div className="split-layout">
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

      <div className="panel aside-panel">
        <FeatureIcon kind="account" />
        <h2>{t('openAccount.aside.title')}</h2>
        <ul className="feature-list">
          <li>{t('openAccount.aside.point1')}</li>
          <li>{t('openAccount.aside.point2')}</li>
          <li>{t('openAccount.aside.point3')}</li>
        </ul>
      </div>
    </div>
  );
}

