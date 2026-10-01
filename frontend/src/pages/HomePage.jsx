import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { users } from '../api/bank';
import Alert from '../components/Alert';
import BrandMark from '../components/BrandMark';
import Spinner from '../components/Spinner';
import { formatAccountType, formatDate, formatMoney, localeFor, totalBalance } from '../components/format';

/** Section 7.1: "Create Account" and "View Account" when logged out; your accounts when logged in. */
export default function HomePage() {
  const { user, isAdmin } = useAuth();
  if (!user) return <Welcome />;
  if (isAdmin) return <Navigate to="/admin/users" replace />;
  return <Dashboard user={user} />;
}

function Welcome() {
  const { t } = useLanguage();
  return (
    <section className="hero">
      <div className="hero-text">
        <h1>{t('home.hero.title')}</h1>
        <p>{t('home.hero.body')}</p>
        <div className="button-row">
          <Link to="/create-account" className="button">{t('home.hero.createAccount')}</Link>
          <Link to="/login" className="button secondary">{t('home.hero.viewAccount')}</Link>
        </div>
      </div>

      <div className="panel hero-logo" aria-hidden="true">
        <BrandMark size={200} />
      </div>
    </section>
  );
}

function Dashboard({ user }) {
  const { t, language } = useLanguage();
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
        <h1>{t('home.hello', { name: firstName })}</h1>
        <Link to="/accounts/new" className="button">{t('home.openAccount')}</Link>
      </div>

      <Alert>{error}</Alert>

      {myAccounts === null && !error && <Spinner label={t('home.loading')} />}

      {myAccounts?.length === 0 && (
        <div className="panel empty">
          <h2>{t('home.empty.title')}</h2>
          <p className="muted">{t('home.empty.body')}</p>
          <Link to="/accounts/new" className="button">{t('home.openAccount')}</Link>
        </div>
      )}

      {myAccounts?.length > 0 && (
        <div className="table-wrap">
          <table className="ledger">
            <caption>{t('home.table.caption')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('home.table.account')}</th>
                <th scope="col">{t('home.table.opened')}</th>
                <th scope="col" className="num">{t('home.table.balance')}</th>
                <th scope="col"><span className="visually-hidden">{t('home.table.viewAccount')}</span></th>
              </tr>
            </thead>
            <tbody>
              {myAccounts.map((account) => (
                <tr key={account.accountId}>
                  <td>{formatAccountType(account.accountType, t)} #{account.accountId}</td>
                  <td>{formatDate(account.createdAt, localeFor(language))}</td>
                  <td className="num">{formatMoney(account.balance)}</td>
                  <td className="actions">
                    <Link to={`/accounts/${account.accountId}`}>{t('home.table.viewAccount')}</Link>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row" colSpan={2}>{t('home.table.total')}</th>
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

