import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { users } from '../api/bank';
import Alert from '../components/Alert';
import { formatAccountType, formatDate, formatMoney, totalBalance } from '../components/format';

/** Section 7.1: "Create Account" and "View Account" when logged out; your accounts when logged in. */
export default function HomePage() {
  const { user, isAdmin } = useAuth();
  if (!user) return <Welcome />;
  if (isAdmin) return <Navigate to="/admin/users" replace />;
  return <Dashboard user={user} />;
}

function Welcome() {
  return (
    <section className="hero">
      <div className="hero-text">
        <h1>Every deposit, withdrawal, and transfer, accounted for.</h1>
        <p>
          Open a savings or checking account in a minute. Move money between accounts
          and see a full history of everything that happened.
        </p>
        <div className="button-row">
          <Link to="/create-account" className="button">Create account</Link>
          <Link to="/login" className="button secondary">View account</Link>
        </div>
      </div>

      <div className="panel statement" aria-hidden="true">
        <p className="muted">Savings account #1024</p>
        <p className="balance">$2,480.00</p>
        <table className="ledger compact">
          <tbody>
            <tr><td>Deposit</td><td className="num credit">+$1,500.00</td></tr>
            <tr><td>Transfer to #1031</td><td className="num debit">−$200.00</td></tr>
            <tr><td>Deposit</td><td className="num credit">+$1,180.00</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Dashboard({ user }) {
  const location = useLocation();
  const [myAccounts, setMyAccounts] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false; // don't update state if the page was left before the answer came back
    users
      .accounts(user.userId)
      .then((list) => { if (!ignore) setMyAccounts(list); })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [user.userId]);

  const firstName = user.name.split(' ')[0];

  return (
    <>
      <div className="page-header">
        <h1>Hello, {firstName}</h1>
        <Link to="/accounts/new" className="button">Open an account</Link>
      </div>

      <Alert kind="info">{location.state?.notice}</Alert>
      <Alert>{error}</Alert>

      {myAccounts === null && !error && <p className="muted">Loading your accounts…</p>}

      {myAccounts?.length === 0 && (
        <div className="panel empty">
          <h2>You don't have an account yet</h2>
          <p className="muted">Open a savings or checking account to start depositing money.</p>
          <Link to="/accounts/new" className="button">Open an account</Link>
        </div>
      )}

      {myAccounts?.length > 0 && (
        <div className="table-wrap">
          <table className="ledger">
            <caption>Your accounts</caption>
            <thead>
              <tr>
                <th scope="col">Account</th>
                <th scope="col">Opened</th>
                <th scope="col" className="num">Balance</th>
                <th scope="col"><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {myAccounts.map((account) => (
                <tr key={account.accountId}>
                  <td>{formatAccountType(account.accountType)} #{account.accountId}</td>
                  <td>{formatDate(account.createdAt)}</td>
                  <td className="num">{formatMoney(account.balance)}</td>
                  <td className="actions">
                    <Link to={`/accounts/${account.accountId}`}>View account</Link>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row" colSpan={2}>Total</th>
                <td className="num">{formatMoney(totalBalance(myAccounts))}</td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </>
  );
}
