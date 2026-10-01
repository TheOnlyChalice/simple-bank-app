import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
import { accounts, scheduledTransfers, users } from '../api/bank';
import Alert from '../components/Alert';
import FeatureIcon from '../components/FeatureIcon';
import FormField from '../components/FormField';
import Spinner from '../components/Spinner';
import { formatAccountType, formatDateTime, formatMoney, localeFor } from '../components/format';
import { useForm } from '../components/useForm';

/** The value a <input type="datetime-local"> expects ("2026-10-15T14:30"), in local time. */
function toLocalInputValue(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * Move money from one of your own accounts to any account, now or at a chosen date and time.
 * Scheduled transfers are checked for funds when they run, not when they're scheduled.
 */
export default function TransferPage() {
  const { user } = useAuth();
  const { t, language } = useLanguage();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [myAccounts, setMyAccounts] = useState(null);
  const [loadError, setLoadError] = useState('');
  const form = useForm({
    fromAccountId: searchParams.get('from') ?? '',
    toAccountId: '',
    amount: '',
    when: 'now',
    scheduledFor: '',
  });

  useEffect(() => {
    let ignore = false;
    users
      .accounts(user.userId)
      .then((list) => {
        if (ignore) return;
        setMyAccounts(list);
        if (!form.values.fromAccountId && list.length > 0) {
          form.setValues((current) => ({ ...current, fromAccountId: String(list[0].accountId) }));
        }
      })
      .catch((err) => { if (!ignore) setLoadError(err.message); });
    return () => { ignore = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.userId]);

  const scheduling = form.values.when === 'later';

  async function handleSubmit(event) {
    event.preventDefault();
    const values = form.values;

    if (scheduling) {
      // The box holds local time; the backend wants a UTC timestamp
      const scheduledFor = values.scheduledFor ? new Date(values.scheduledFor).toISOString() : null;
      const created = await form.submit((v) =>
        scheduledTransfers.create(v.fromAccountId, v.toAccountId, v.amount, scheduledFor));
      if (created) {
        showToast(t('transfer.scheduledNotice', {
          amount: formatMoney(created.amount),
          id: created.toAccountId,
          date: formatDateTime(created.scheduledFor, localeFor(language)),
        }));
        navigate('/transfers/scheduled');
      }
      return;
    }

    const result = await form.submit((v) => accounts.transfer(v.fromAccountId, v.toAccountId, v.amount));
    if (result) {
      showToast(t('transfer.notice', { amount: formatMoney(result.amount), id: values.toAccountId }));
      navigate(`/accounts/${values.fromAccountId}`);
    }
  }

  const { errors, bind } = form;
  const now = new Date();
  const earliest = toLocalInputValue(new Date(now.getTime() + 2 * 60 * 1000)); // a little over the 1-minute minimum
  const latest = toLocalInputValue(new Date(now.getTime() + 365 * 24 * 60 * 60 * 1000));

  return (
    <div className="split-layout">
      <div className="narrow">
        <h1>{t('transfer.title')}</h1>
        <Alert>{loadError}</Alert>
        <Alert>{form.message}</Alert>

        {myAccounts === null && !loadError && <Spinner label={t('transfer.loading')} />}

        {myAccounts?.length === 0 && (
          <div className="panel empty">
            <p className="muted">{t('transfer.noAccounts')}</p>
          </div>
        )}

        {myAccounts?.length > 0 && (
          <form className="panel stack" onSubmit={handleSubmit} noValidate>
            <FormField label={t('transfer.fromAccount')} as="select" error={errors.fromAccountId} {...bind('fromAccountId')}>
              {myAccounts.map((account) => (
                <option key={account.accountId} value={account.accountId}>
                  {formatAccountType(account.accountType, t)} #{account.accountId} — {formatMoney(account.balance)}
                  {account.frozen ? ` (${t('account.frozenBadge')})` : ''}
                </option>
              ))}
            </FormField>
            <FormField label={t('transfer.toAccountId')} type="number" inputMode="numeric"
              error={errors.toAccountId} {...bind('toAccountId')} />
            <FormField label={t('transfer.amount')} type="number" step="0.01" min="0.01" inputMode="decimal"
              error={errors.amount} {...bind('amount')} />

            <fieldset className="choice-group">
              <legend>{t('transfer.when')}</legend>
              <label className="choice">
                <input type="radio" name="when" value="now" checked={!scheduling}
                  onChange={() => form.setValues((c) => ({ ...c, when: 'now' }))} />
                {t('transfer.now')}
              </label>
              <label className="choice">
                <input type="radio" name="when" value="later" checked={scheduling}
                  onChange={() => form.setValues((c) => ({ ...c, when: 'later', scheduledFor: c.scheduledFor || earliest }))} />
                {t('transfer.later')}
              </label>
            </fieldset>

            {scheduling && (
              <FormField label={t('transfer.scheduledFor')} type="datetime-local" min={earliest} max={latest}
                hint={t('transfer.scheduledForHint')} error={errors.scheduledFor} {...bind('scheduledFor')} />
            )}

            <button type="submit" className="button" disabled={form.submitting}>
              {scheduling
                ? (form.submitting ? t('transfer.scheduling') : t('transfer.scheduleSubmit'))
                : (form.submitting ? t('transfer.submitting') : t('transfer.submit'))}
            </button>
          </form>
        )}

        <p className="after-panel"><Link to="/transfers/scheduled">{t('transfer.viewScheduled')}</Link></p>
      </div>

      <div className="panel aside-panel">
        <FeatureIcon kind="transfer" />
        <h2>{t('transfer.aside.title')}</h2>
        <ul className="feature-list">
          <li>{t('transfer.aside.point1')}</li>
          <li>{t('transfer.aside.point2')}</li>
          <li>{t('transfer.aside.point3')}</li>
          <li>{t('transfer.aside.point4')}</li>
        </ul>
      </div>
    </div>
  );
}
