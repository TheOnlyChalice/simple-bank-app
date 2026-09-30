import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { users } from '../../api/bank';
import Alert from '../../components/Alert';
import { formatAccountType, formatDate, formatMoney, totalBalance } from '../../components/format';

/** Staff-only: one customer's profile and accounts. */
export default function AdminUserDetailsPage() {
  const { userId } = useParams();
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [accountList, setAccountList] = useState(null);
  const [error, setError] = useState('');
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    let ignore = false;
    Promise.all([users.get(userId), users.accounts(userId)])
      .then(([loadedUser, loadedAccounts]) => {
        if (ignore) return;
        setUser(loadedUser);
        setAccountList(loadedAccounts);
      })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [userId]);

  async function handleDelete() {
    if (!window.confirm(`Close ${user.name}'s profile? This cannot be undone.`)) return;
    setDeleting(true);
    try {
      await users.remove(userId);
      navigate('/admin/users', { state: { notice: `${user.name}'s profile has been closed.` } });
    } catch (err) {
      setError(err.message);
      setDeleting(false);
    }
  }

  if (error) {
    return (
      <div className="narrow stack">
        <Alert>{error}</Alert>
        <Link to="/admin/users">Back to customers</Link>
      </div>
    );
  }
  if (!user) return <p className="muted">Loading customer…</p>;

  return (
    <div className="stack-lg">
      <div className="page-header">
        <h1>{user.name}</h1>
        <Link to="/admin/users" className="button secondary">Back to customers</Link>
      </div>

      <section className="panel">
        <dl className="facts">
          <div><dt>Email</dt><dd>{user.email}</dd></div>
          <div><dt>Role</dt><dd>{user.role}</dd></div>
          <div><dt>Customer since</dt><dd>{formatDate(user.createdAt)}</dd></div>
          {user.address && (
            <div>
              <dt>Address</dt>
              <dd>{user.address.street}, {user.address.city}, {user.address.state} {user.address.zip}</dd>
            </div>
          )}
        </dl>
      </section>

      <section className="stack">
        <h2>Accounts</h2>
        {accountList?.length === 0 && <p className="muted">This customer has no accounts.</p>}
        {accountList?.length > 0 && (
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">Account</th>
                  <th scope="col">Opened</th>
                  <th scope="col" className="num">Balance</th>
                  <th scope="col"><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {accountList.map((account) => (
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
                  <td className="num">{formatMoney(totalBalance(accountList))}</td>
                  <td />
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </section>

      <section className="panel stack">
        <h2>Close profile</h2>
        <p className="muted">
          This permanently closes the customer's profile. It cannot be undone, and all their accounts
          must be closed first.
        </p>
        <button type="button" className="button danger" onClick={handleDelete} disabled={deleting}>
          {deleting ? 'Closing…' : 'Close profile'}
        </button>
      </section>
    </div>
  );
}
