import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import TransactionTable from '../components/TransactionTable';
import { formatAccountType, formatDate, formatMoney } from '../components/format';

/** Section 7.3: account ID, user name, and balance, with Deposit, Withdraw, and View Transactions. */
export default function AccountDetailsPage() {
  const { accountId } = useParams();
  const location = useLocation();
  const [account, setAccount] = useState(null);
  const [recent, setRecent] = useState([]);
  const [error, setError] = useState('');

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
    </div>
  );
}
