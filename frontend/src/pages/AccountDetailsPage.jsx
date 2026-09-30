import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import TransactionTable from '../components/TransactionTable';
import { formatAccountType, formatDate, formatMoney } from '../components/format';

/** Section 7.3: account ID, user name, and balance, with Deposit, Withdraw, and View Transactions. */
export default function AccountDetailsPage() {
  const { accountId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [account, setAccount] = useState(null);
  const [recent, setRecent] = useState([]);
  const [error, setError] = useState('');
  const [settingsMessage, setSettingsMessage] = useState('');
  const [savingType, setSavingType] = useState(false);
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    let ignore = false;
    setAccount(null);
    setError('');
    Promise.all([accounts.get(accountId), accounts.transactions(accountId, 0, 5)])
      .then(([loaded, history]) => {
        if (ignore) return;
        setAccount(loaded);
        setRecent(history.content);
      })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [accountId]);

  async function handleTypeChange(event) {
    const accountType = event.target.value;
    setSavingType(true);
    setSettingsMessage('');
    try {
      const updated = await accounts.update(accountId, accountType);
      setAccount(updated);
    } catch (err) {
      setSettingsMessage(err.message);
    } finally {
      setSavingType(false);
    }
  }

  async function handleClose() {
    if (!window.confirm('Close this account? This cannot be undone.')) return;
    setClosing(true);
    setSettingsMessage('');
    try {
      await accounts.remove(accountId);
      navigate('/', { state: { notice: 'The account has been closed.' } });
    } catch (err) {
      setSettingsMessage(err.message);
      setClosing(false);
    }
  }

  if (error) {
    return (
      <div className="narrow stack">
        <Alert>{error}</Alert>
        <Link to="/">Back to your accounts</Link>
      </div>
    );
  }
  if (!account) return <p className="muted">Loading account…</p>;

  const base = `/accounts/${account.accountId}`;

  return (
    <div className="stack-lg">
      <Alert kind="success">{location.state?.notice}</Alert>

      <section className="panel account-summary">
        <div>
          <h1>{formatAccountType(account.accountType)} account</h1>
          <dl className="facts">
            <div><dt>Account ID</dt><dd>#{account.accountId}</dd></div>
            <div><dt>Account holder</dt><dd>{account.userName}</dd></div>
            <div><dt>Opened</dt><dd>{formatDate(account.createdAt)}</dd></div>
          </dl>
        </div>
        <div className="balance-block">
          <p className="muted">Available balance</p>
          <p className="balance">{formatMoney(account.balance)}</p>
        </div>
      </section>

      <div className="button-row">
        <Link to={`${base}/deposit`} className="button">Deposit</Link>
        <Link to={`${base}/withdraw`} className="button">Withdraw</Link>
        <Link to={`/transfer?from=${account.accountId}`} className="button secondary">Transfer</Link>
        <Link to={`${base}/transactions`} className="button secondary">View transactions</Link>
      </div>

      <section className="stack">
        <h2>Recent activity</h2>
        <TransactionTable transactions={recent} />
      </section>

      <section className="panel stack">
        <h2>Account settings</h2>
        <Alert>{settingsMessage}</Alert>
        <div className="field">
          <label htmlFor="accountType">Account type</label>
          <select id="accountType" value={account.accountType} onChange={handleTypeChange} disabled={savingType}>
            <option value="SAVINGS">Savings</option>
            <option value="CHECKING">Checking</option>
          </select>
        </div>
        <button type="button" className="button danger" onClick={handleClose} disabled={closing}>
          {closing ? 'Closing…' : 'Close account'}
        </button>
        <p className="muted">The balance must be $0.00 before an account can be closed.</p>
      </section>
    </div>
  );
}

