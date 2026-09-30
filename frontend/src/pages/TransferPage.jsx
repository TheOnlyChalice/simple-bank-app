import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { accounts, users } from '../api/bank';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { formatAccountType, formatMoney } from '../components/format';
import { useForm } from '../components/useForm';

/** Move money from one of your own accounts to any account. */
export default function TransferPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [myAccounts, setMyAccounts] = useState(null);
  const [loadError, setLoadError] = useState('');
  const form = useForm({ fromAccountId: searchParams.get('from') ?? '', toAccountId: '', amount: '' });

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

  async function handleSubmit(event) {
    event.preventDefault();
    const values = form.values;
    const result = await form.submit((v) => accounts.transfer(v.fromAccountId, v.toAccountId, v.amount));
    if (result) {
      navigate(`/accounts/${values.fromAccountId}`, {
        state: { notice: `Transferred ${formatMoney(result.amount)} to account #${values.toAccountId}.` },
      });
    }
  }

  const { errors, bind } = form;

  return (
    <div className="narrow">
      <h1>Transfer</h1>
      <Alert>{loadError}</Alert>
      <Alert>{form.message}</Alert>

      {myAccounts === null && !loadError && <p className="muted">Loading your accounts…</p>}

      {myAccounts?.length === 0 && (
        <div className="panel empty">
          <p className="muted">You don't have an account to transfer from yet.</p>
        </div>
      )}

      {myAccounts?.length > 0 && (
        <form className="panel stack" onSubmit={handleSubmit} noValidate>
          <FormField label="From account" as="select" error={errors.fromAccountId} {...bind('fromAccountId')}>
            {myAccounts.map((account) => (
              <option key={account.accountId} value={account.accountId}>
                {formatAccountType(account.accountType)} #{account.accountId} — {formatMoney(account.balance)}
              </option>
            ))}
          </FormField>
          <FormField label="To account ID" type="number" inputMode="numeric"
            error={errors.toAccountId} {...bind('toAccountId')} />
          <FormField label="Amount" type="number" step="0.01" min="0.01" inputMode="decimal"
            error={errors.amount} {...bind('amount')} />
          <button type="submit" className="button" disabled={form.submitting}>
            {form.submitting ? 'Transferring…' : 'Transfer'}
          </button>
        </form>
      )}
    </div>
  );
}
