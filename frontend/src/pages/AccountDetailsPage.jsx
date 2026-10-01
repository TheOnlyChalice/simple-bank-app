import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { accounts } from '../api/bank';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
import Alert from '../components/Alert';
import Spinner from '../components/Spinner';
import TransactionTable from '../components/TransactionTable';
import { useConfirm } from '../components/useConfirm';
import { formatDate, formatDateTime, formatMoney, localeFor } from '../components/format';

/**
 * Section 7.3: account ID, user name, and balance, with Deposit, Withdraw, and View Transactions.
 * A frozen account shows a banner, and its money buttons are disabled until it's unfrozen.
 */
export default function AccountDetailsPage() {
  const { accountId } = useParams();
  const { isAdmin } = useAuth();
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
  const [changingFreeze, setChangingFreeze] = useState(false);

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

  async function handleFreezeToggle() {
    const freezing = !account.frozen;
    if (freezing && !(await confirm(t('account.freezeConfirm')))) return;
    setChangingFreeze(true);
    setSettingsMessage('');
    try {
      const updated = freezing ? await accounts.freeze(accountId) : await accounts.unfreeze(accountId);
      setAccount(updated);
      showToast(freezing ? t('account.frozenNotice') : t('account.unfrozenNotice'));
    } catch (err) {
      setSettingsMessage(err.message);
    } finally {
      setChangingFreeze(false);
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
  const locale = localeFor(language);
  const frozenByBank = account.frozen && account.frozenBy === 'ADMIN';
  // Customers can't lift a freeze made by the bank; staff can lift any freeze
  const canChangeFreeze = !frozenByBank || isAdmin;

  return (
    <div className="stack-lg">
      {dialog}

      {account.frozen && (
        <div className="alert frozen" role="status">
          <strong>{t('account.frozenTitle')}</strong>
          <p>{t('account.frozenBody')}</p>
          <p className="muted">
            {frozenByBank
              ? t('account.frozenByStaff', { date: formatDateTime(account.frozenAt, locale) })
              : t('account.frozenByCustomer', { date: formatDateTime(account.frozenAt, locale) })}
          </p>
          {frozenByBank && !isAdmin && <p>{t('account.frozenByBank')}</p>}
        </div>
      )}

      <section className="panel account-summary">
        <div>
          <h1>
            {title}
            {account.frozen && <span className="badge frozen title-badge">{t('account.frozenBadge')}</span>}
          </h1>
          <dl className="facts">
            <div><dt>{t('account.accountId')}</dt><dd>#{account.accountId}</dd></div>
            <div><dt>{t('account.holder')}</dt><dd>{account.userName}</dd></div>
            <div><dt>{t('account.opened')}</dt><dd>{formatDate(account.createdAt, locale)}</dd></div>
          </dl>
        </div>
        <div className="balance-block">
          <p className="muted">{t('account.availableBalance')}</p>
          <p className="balance">{formatMoney(account.balance)}</p>
        </div>
      </section>

      <div className="button-row">
        {account.frozen ? (
          // Shown but disabled while frozen, so it's clear what is paused
          <>
            <button type="button" className="button" disabled>{t('account.deposit')}</button>
            <button type="button" className="button" disabled>{t('account.withdraw')}</button>
            <button type="button" className="button secondary" disabled>{t('account.transfer')}</button>
          </>
        ) : (
          <>
            <Link to={`${base}/deposit`} className="button">{t('account.deposit')}</Link>
            <Link to={`${base}/withdraw`} className="button">{t('account.withdraw')}</Link>
            <Link to={`/transfer?from=${account.accountId}`} className="button secondary">{t('account.transfer')}</Link>
          </>
        )}
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

        <div className="stack setting-group">
          <h3>{account.frozen ? t('account.unfreeze') : t('account.freeze')}</h3>
          <p className="muted">{account.frozen ? t('account.unfreezeHint') : t('account.freezeHint')}</p>
          {canChangeFreeze ? (
            <button type="button" className="button secondary" onClick={handleFreezeToggle} disabled={changingFreeze}>
              {account.frozen
                ? (changingFreeze ? t('account.unfreezing') : t('account.unfreeze'))
                : (changingFreeze ? t('account.freezing') : t('account.freeze'))}
            </button>
          ) : (
            <p className="muted">{t('account.frozenByBank')}</p>
          )}
        </div>

        <div className="stack setting-group">
          <button type="button" className="button danger" onClick={handleClose} disabled={closing || account.frozen}>
            {closing ? t('account.closing') : t('account.closeAccount')}
          </button>
          <p className="muted">{account.frozen ? t('account.closeFrozenHint') : t('account.closeHint')}</p>
        </div>
      </section>
    </div>
  );
}
