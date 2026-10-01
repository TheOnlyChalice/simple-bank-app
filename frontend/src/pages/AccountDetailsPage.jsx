import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { accounts } from '../api/bank';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
import Alert from '../components/Alert';
import Spinner from '../components/Spinner';
import TransactionTable from '../components/TransactionTable';
import { useConfirm } from '../components/useConfirm';
import { formatDate, formatMoney, localeFor } from '../components/format';

/** Section 7.3: account ID, user name, and balance, with Deposit, Withdraw, and View Transactions. */
export default function AccountDetailsPage() {
  const { accountId } = useParams();
  const { t, language } = useLanguage();
  const { showToast } = useToast();
  const { confirm, dialog } = useConfirm();
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
    if (!(await confirm(t('account.closeConfirm')))) return;
    setClosing(true);
    setSettingsMessage('');
    try {
      await accounts.remove(accountId);
      showToast(t('account.closedNotice'));
      navigate('/');
    } catch (err) {
      setSettingsMessage(err.message);
      setClosing(false);
    }
  }

  if (error) {
    return (
      <div className="narrow stack">
        <Alert>{error}</Alert>
        <Link to="/">{t('account.backToAccounts')}</Link>
      </div>
    );
  }
  if (!account) return <Spinner label={t('account.loading')} />;

  const base = `/accounts/${account.accountId}`;
  const title = account.accountType === 'CHECKING' ? t('account.checkingAccount') : t('account.savingsAccount');

  return (
    <div className="stack-lg">
      {dialog}

      <section className="panel account-summary">
        <div>
          <h1>{title}</h1>
          <dl className="facts">
            <div><dt>{t('account.accountId')}</dt><dd>#{account.accountId}</dd></div>
            <div><dt>{t('account.holder')}</dt><dd>{account.userName}</dd></div>
            <div><dt>{t('account.opened')}</dt><dd>{formatDate(account.createdAt, localeFor(language))}</dd></div>
          </dl>
        </div>
        <div className="balance-block">
          <p className="muted">{t('account.availableBalance')}</p>
          <p className="balance">{formatMoney(account.balance)}</p>
        </div>
      </section>

      <div className="button-row">
        <Link to={`${base}/deposit`} className="button">{t('account.deposit')}</Link>
        <Link to={`${base}/withdraw`} className="button">{t('account.withdraw')}</Link>
        <Link to={`/transfer?from=${account.accountId}`} className="button secondary">{t('account.transfer')}</Link>
        <Link to={`${base}/transactions`} className="button secondary">{t('account.viewTransactions')}</Link>
      </div>

      <section className="stack">
        <h2>{t('account.recentActivity')}</h2>
        <TransactionTable transactions={recent} />
      </section>

      <section className="panel stack">
        <h2>{t('account.settings')}</h2>
        <Alert>{settingsMessage}</Alert>
        <div className="field">
          <label htmlFor="accountType">{t('account.accountType')}</label>
          <select id="accountType" value={account.accountType} onChange={handleTypeChange} disabled={savingType}>
            <option value="SAVINGS">{t('createAccount.savings')}</option>
            <option value="CHECKING">{t('createAccount.checking')}</option>
          </select>
        </div>
        <button type="button" className="button danger" onClick={handleClose} disabled={closing}>
          {closing ? t('account.closing') : t('account.closeAccount')}
        </button>
        <p className="muted">{t('account.closeHint')}</p>
      </section>
    </div>
  );
}


