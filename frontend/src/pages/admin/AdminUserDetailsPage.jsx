import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { users } from '../../api/bank';
import { useLanguage } from '../../i18n/LanguageContext';
import Alert from '../../components/Alert';
import { formatAccountType, formatDate, formatMoney, localeFor, totalBalance } from '../../components/format';

/** Staff-only: one customer's profile and accounts. */
export default function AdminUserDetailsPage() {
  const { userId } = useParams();
  const { t, language } = useLanguage();
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
    if (!window.confirm(t('adminUserDetails.closeConfirm', { name: user.name }))) return;
    setDeleting(true);
    try {
      await users.remove(userId);
      navigate('/admin/users', { state: { notice: t('adminUserDetails.closedNotice', { name: user.name }) } });
    } catch (err) {
      setError(err.message);
      setDeleting(false);
    }
  }

  if (error) {
    return (
      <div className="narrow stack">
        <Alert>{error}</Alert>
        <Link to="/admin/users">{t('adminUserDetails.backToCustomers')}</Link>
      </div>
    );
  }
  if (!user) return <p className="muted">{t('adminUserDetails.loading')}</p>;

  return (
    <div className="stack-lg">
      <div className="page-header">
        <h1>{user.name}</h1>
        <Link to="/admin/users" className="button secondary">{t('adminUserDetails.backToCustomers')}</Link>
      </div>

      <section className="panel">
        <dl className="facts">
          <div><dt>{t('adminUserDetails.email')}</dt><dd>{user.email}</dd></div>
          <div><dt>{t('adminUserDetails.role')}</dt><dd>{user.role}</dd></div>
          <div><dt>{t('adminUserDetails.since')}</dt><dd>{formatDate(user.createdAt, localeFor(language))}</dd></div>
          {user.address && (
            <div>
              <dt>{t('adminUserDetails.address')}</dt>
              <dd>{user.address.street}, {user.address.city}, {user.address.state} {user.address.zip}</dd>
            </div>
          )}
        </dl>
      </section>

      <section className="stack">
        <h2>{t('adminUserDetails.accountsTitle')}</h2>
        {accountList?.length === 0 && <p className="muted">{t('adminUserDetails.noAccounts')}</p>}
        {accountList?.length > 0 && (
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">{t('adminUserDetails.account')}</th>
                  <th scope="col">{t('adminUserDetails.opened')}</th>
                  <th scope="col" className="num">{t('adminUserDetails.balance')}</th>
                  <th scope="col"><span className="visually-hidden">{t('adminUserDetails.viewAccount')}</span></th>
                </tr>
              </thead>
              <tbody>
                {accountList.map((account) => (
                  <tr key={account.accountId}>
                    <td>{formatAccountType(account.accountType, t)} #{account.accountId}</td>
                    <td>{formatDate(account.createdAt, localeFor(language))}</td>
                    <td className="num">{formatMoney(account.balance)}</td>
                    <td className="actions">
                      <Link to={`/accounts/${account.accountId}`}>{t('adminUserDetails.viewAccount')}</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row" colSpan={2}>{t('adminUserDetails.total')}</th>
                  <td className="num">{formatMoney(totalBalance(accountList))}</td>
                  <td />
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </section>

      <section className="panel stack">
        <h2>{t('adminUserDetails.closeTitle')}</h2>
        <p className="muted">{t('adminUserDetails.closeBody')}</p>
        <button type="button" className="button danger" onClick={handleDelete} disabled={deleting}>
          {deleting ? t('adminUserDetails.closing') : t('adminUserDetails.closeSubmit')}
        </button>
      </section>
    </div>
  );
}

