import { Link, useNavigate, useParams } from 'react-router';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { formatMoney } from '../components/format';
import { useForm } from '../components/useForm';

/** Withdraw money from one account. */
export default function WithdrawPage() {
  const { accountId } = useParams();
  const navigate = useNavigate();
  const form = useForm({ amount: '' });

  async function handleSubmit(event) {
    event.preventDefault();
    const account = await form.submit((values) => accounts.withdraw(accountId, values.amount));
    if (account) {
      navigate(`/accounts/${accountId}`, {
        state: { notice: `Withdrew ${formatMoney(form.values.amount)}. New balance: ${formatMoney(account.balance)}.` },
      });
    }
  }

  return (
    <div className="narrow">
      <h1>Withdraw</h1>
      <p className="muted">Account #{accountId}</p>
      <Alert>{form.message}</Alert>
      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label="Amount" type="number" step="0.01" min="0.01" inputMode="decimal"
          error={form.errors.amount} {...form.bind('amount')} />
        <div className="button-row">
          <button type="submit" className="button" disabled={form.submitting}>
            {form.submitting ? 'Withdrawing…' : 'Withdraw'}
          </button>
          <Link to={`/accounts/${accountId}`} className="button secondary">Cancel</Link>
        </div>
      </form>
    </div>
  );
}
