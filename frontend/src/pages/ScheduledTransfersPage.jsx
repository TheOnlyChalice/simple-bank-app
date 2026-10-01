import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { scheduledTransfers } from '../api/bank';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
import Alert from '../components/Alert';
import Pagination from '../components/Pagination';
import Spinner from '../components/Spinner';
import { useConfirm } from '../components/useConfirm';
import { formatDateTime, formatMoney, localeFor } from '../components/format';

const STATUSES = ['PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'];
const PAGE_SIZE = 20;

const STATUS_BADGE = {
  PENDING: 'badge',
  COMPLETED: 'badge success',
  FAILED: 'badge failed',
  CANCELLED: 'badge muted-badge',
};

/**
 * Transfers set to run at a future date and time. Customers see their own (the backend
 * only returns theirs); staff see everyone's. Pending transfers can be cancelled.
 */
export default function ScheduledTransfersPage() {
  const { isAdmin } = useAuth();
  const { t, language } = useLanguage();
  const { showToast } = useToast();
  const { confirm, dialog } = useConfirm();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [cancellingId, setCancellingId] = useState(null);

  useEffect(() => {
    let ignore = false;
    setError('');
    scheduledTransfers
      .list({ status, page, size: PAGE_SIZE })
      .then((data) => { if (!ignore) setPageData(data); })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [status, page, reloadKey]);

  async function handleCancel(transfer) {
    if (!(await confirm(t('scheduled.cancelConfirm', { amount: formatMoney(transfer.amount) })))) return;
    setCancellingId(transfer.scheduledTransferId);
    setError('');
    try {
      await scheduledTransfers.cancel(transfer.scheduledTransferId);
      showToast(t('scheduled.cancelledNotice'));
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err.message);
    } finally {
      setCancellingId(null);
    }
  }

  const locale = localeFor(language);

  return (
    <div className="stack-lg">
      {dialog}
      <div className="page-header">
        <div>
          <h1>{t('scheduled.title')}</h1>
          <p className="muted">{t('scheduled.intro')}</p>
        </div>
        {!isAdmin && <Link to="/transfer" className="button">{t('scheduled.new')}</Link>}
      </div>

      <div className="field status-filter">
        <label htmlFor="status">{t('scheduled.status')}</label>
        <select id="status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">{t('scheduled.all')}</option>
          {STATUSES.map((s) => <option key={s} value={s}>{t(`scheduled.status.${s}`)}</option>)}
        </select>
      </div>

      <Alert>{error}</Alert>
      {pageData === null && !error && <Spinner label={t('scheduled.loading')} />}
      {pageData?.content.length === 0 && <p className="muted">{t('scheduled.empty')}</p>}

      {pageData?.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">{t('scheduled.when')}</th>
                  {isAdmin && <th scope="col">{t('scheduled.customer')}</th>}
                  <th scope="col">{t('scheduled.from')}</th>
                  <th scope="col">{t('scheduled.to')}</th>
                  <th scope="col" className="num">{t('scheduled.amount')}</th>
                  <th scope="col">{t('scheduled.status')}</th>
                  <th scope="col"><span className="visually-hidden">{t('scheduled.actions')}</span></th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((transfer) => (
                  <tr key={transfer.scheduledTransferId}>
                    <td className="nowrap">{formatDateTime(transfer.scheduledFor, locale)}</td>
                    {isAdmin && (
                      <td><Link to={`/admin/users/${transfer.ownerUserId}`}>#{transfer.ownerUserId}</Link></td>
                    )}
                    <td><Link to={`/accounts/${transfer.fromAccountId}`}>#{transfer.fromAccountId}</Link></td>
                    <td>#{transfer.toAccountId}</td>
                    <td className="num">{formatMoney(transfer.amount)}</td>
                    <td>
                      <span className={STATUS_BADGE[transfer.status] ?? 'badge'}>
                        {t(`scheduled.status.${transfer.status}`)}
                      </span>
                      {transfer.status === 'FAILED' && transfer.failureReason && (
                        <div className="muted small-note">{transfer.failureReason}</div>
                      )}
                      {transfer.processedAt && transfer.status !== 'PENDING' && (
                        <div className="muted small-note">
                          {t('scheduled.processedAt', { date: formatDateTime(transfer.processedAt, locale) })}
                        </div>
                      )}
                    </td>
                    <td className="actions">
                      {transfer.status === 'PENDING' && (
                        <button type="button" className="button small danger"
                          onClick={() => handleCancel(transfer)}
                          disabled={cancellingId === transfer.scheduledTransferId}>
                          {t('scheduled.cancelTransfer')}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={pageData.page} totalPages={pageData.totalPages}
            first={pageData.first} last={pageData.last} onChange={setPage} />
        </>
      )}
    </div>
  );
}
