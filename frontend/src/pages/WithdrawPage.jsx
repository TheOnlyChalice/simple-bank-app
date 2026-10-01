import { Link, useNavigate, useParams } from 'react-router';
import { accounts } from '../api/bank';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { formatMoney } from '../components/format';
import { useForm } from '../components/useForm';

/** Withdraw money from one account. */
export default function WithdrawPage() {
  const { accountId } = useParams();
  const { t } = useLanguage();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const form = useForm({ amount: '' });

  async function handleSubmit(event) {
    event.preventDefault();
    const account = await form.submit((values) => accounts.withdraw(accountId, values.amount));
    if (account) {
      showToast(t('withdraw.notice', { amount: formatMoney(form.values.amount), balance: formatMoney(account.balance) }));
      navigate(`/accounts/${accountId}`);
    }
  }

  return (
    <div className="narrow">
      <h1>{t('withdraw.title')}</h1>
      <p className="muted">{t('moneyAction.accountLabel', { id: accountId })}</p>
      <Alert>{form.message}</Alert>
      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label={t('withdraw.amount')} type="number" step="0.01" min="0.01" inputMode="decimal"
          error={form.errors.amount} {...form.bind('amount')} />
        <div className="button-row">
          <button type="submit" className="button" disabled={form.submitting}>
            {form.submitting ? t('withdraw.submitting') : t('withdraw.submit')}
          </button>
          <Link to={`/accounts/${accountId}`} className="button secondary">{t('withdraw.cancel')}</Link>
        </div>
      </form>
    </div>
  );
}

