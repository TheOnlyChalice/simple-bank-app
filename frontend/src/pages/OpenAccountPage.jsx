import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { useForm } from '../components/useForm';

/** For customers who are already registered: open another savings or checking account. */
export default function OpenAccountPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const form = useForm({ accountType: 'SAVINGS' });

  async function handleSubmit(event) {
    event.preventDefault();
    const account = await form.submit((values) => accounts.create(user.userId, values.accountType));
    if (account) {
      navigate(`/accounts/${account.accountId}`, { state: { notice: 'Your new account is open.' } });
    }
  }

  return (
    <div className="narrow">
      <h1>Open an account</h1>
      <Alert>{form.message}</Alert>
      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label="Account type" as="select" error={form.errors.accountType} {...form.bind('accountType')}>
          <option value="SAVINGS">Savings</option>
          <option value="CHECKING">Checking</option>
        </FormField>
        <div className="button-row">
          <button type="submit" className="button" disabled={form.submitting}>
            {form.submitting ? 'Opening…' : 'Open account'}
          </button>
          <Link to="/" className="button secondary">Cancel</Link>
        </div>
      </form>
    </div>
  );
}
