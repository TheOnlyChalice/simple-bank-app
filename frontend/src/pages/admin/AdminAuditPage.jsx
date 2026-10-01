import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import Alert from '../../components/Alert';
import FormField from '../../components/FormField';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { formatDateTime, formatMoney, localeFor } from '../../components/format';
import { useLanguage } from '../../i18n/LanguageContext';
import { audit } from '../../api/bank';

const EMPTY_FILTERS = { accountId: '', userId: '', action: '', outcome: '', from: '', to: '' };
const PAGE_SIZE = 20;

const ACTIONS = [
  'USER_CREATED', 'USER_UPDATED', 'USER_DELETED',
  'ACCOUNT_CREATED', 'ACCOUNT_UPDATED', 'ACCOUNT_DELETED',
  'DEPOSIT', 'WITHDRAW', 'TRANSFER', 'LOGIN', 'ACCESS_DENIED',
];
const OUTCOMES = ['SUCCESS', 'REJECTED', 'FAILED'];

function outcomeBadgeClass(outcome) {
  if (outcome === 'SUCCESS') return 'badge success';
  if (outcome === 'REJECTED') return 'badge rejected';
  return 'badge failed';
}

/** Staff-only: the append-only audit trail, searchable by who, what, and when. */
export default function AdminAuditPage() {
  const { t, language } = useLanguage();
  const [draft, setDraft] = useState(EMPTY_FILTERS);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    audit
      .search({
        ...filters,
        from: filters.from ? new Date(filters.from).toISOString() : undefined,
        to: filters.to ? new Date(filters.to).toISOString() : undefined,
        page,
        size: PAGE_SIZE,
      })
      .then((data) => { if (!ignore) setPageData(data); })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [filters, page]);

  function set(name) {
    return (event) => setDraft((current) => ({ ...current, [name]: event.target.value }));
  }

  function handleSearch(event) {
    event.preventDefault();
    setFilters(draft);
    setPage(0);
  }

  return (
    <div className="stack-lg">
      <h1>{t('adminAudit.title')}</h1>

      <form className="panel filters" onSubmit={handleSearch}>
        <FormField label={t('adminAudit.accountId')} type="number" id="accountId" value={draft.accountId} onChange={set('accountId')} />
        <FormField label={t('adminAudit.userId')} type="number" id="userId" value={draft.userId} onChange={set('userId')} />
        <FormField label={t('adminAudit.action')} as="select" id="action" value={draft.action} onChange={set('action')}>
          <option value="">{t('adminAudit.any')}</option>
          {ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
        </FormField>
        <FormField label={t('adminAudit.outcome')} as="select" id="outcome" value={draft.outcome} onChange={set('outcome')}>
          <option value="">{t('adminAudit.any')}</option>
          {OUTCOMES.map((o) => <option key={o} value={o}>{o}</option>)}
        </FormField>
        <FormField label={t('adminAudit.from')} type="datetime-local" id="from" value={draft.from} onChange={set('from')} />
        <FormField label={t('adminAudit.to')} type="datetime-local" id="to" value={draft.to} onChange={set('to')} />
        <div className="button-row">
          <button type="submit" className="button">{t('adminAudit.search')}</button>
          <button type="button" className="button secondary" onClick={() => { setDraft(EMPTY_FILTERS); setFilters(EMPTY_FILTERS); setPage(0); }}>
            {t('adminAudit.reset')}
          </button>
        </div>
      </form>

      <Alert>{error}</Alert>

      {pageData === null && !error && <Spinner label={t('adminAudit.loading')} />}
      {pageData?.content.length === 0 && <p className="muted">{t('adminAudit.empty')}</p>}

      {pageData?.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">{t('adminAudit.when')}</th>
                  <th scope="col">{t('adminAudit.actor')}</th>
                  <th scope="col">{t('adminAudit.action')}</th>
                  <th scope="col">{t('adminAudit.outcome')}</th>
                  <th scope="col">{t('adminAudit.account')}</th>
                  <th scope="col" className="num">{t('adminAudit.amount')}</th>
                  <th scope="col">{t('adminAudit.details')}</th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((event) => (
                  <tr key={event.auditId}>
                    <td>{formatDateTime(event.timestamp, localeFor(language))}</td>
                    <td>{event.actor}</td>
                    <td>{event.action}</td>
                    <td><span className={outcomeBadgeClass(event.outcome)}>{event.outcome}</span></td>
                    <td>
                      {event.accountId ? <Link to={`/accounts/${event.accountId}`}>#{event.accountId}</Link> : '—'}
                      {event.relatedAccountId && (
                        <> → <Link to={`/accounts/${event.relatedAccountId}`}>#{event.relatedAccountId}</Link></>
                      )}
                    </td>
                    <td className="num">{event.amount ? formatMoney(event.amount) : '—'}</td>
                    <td>{event.reason ?? event.details ?? ''}</td>
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

