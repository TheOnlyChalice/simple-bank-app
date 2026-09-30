import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import Alert from '../../components/Alert';
import FormField from '../../components/FormField';
import Pagination from '../../components/Pagination';
import { formatDateTime, formatMoney } from '../../components/format';
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
    return (event) => setFilters((current) => ({ ...current, [name]: event.target.value }));
  }

  function handleSearch(event) {
    event.preventDefault();
    setPage(0);
  }

  return (
    <div className="stack-lg">
      <h1>Audit log</h1>

      <form className="panel filters" onSubmit={handleSearch}>
        <FormField label="Account ID" type="number" id="accountId" value={filters.accountId} onChange={set('accountId')} />
        <FormField label="User ID" type="number" id="userId" value={filters.userId} onChange={set('userId')} />
        <FormField label="Action" as="select" id="action" value={filters.action} onChange={set('action')}>
          <option value="">Any</option>
          {ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
        </FormField>
        <FormField label="Outcome" as="select" id="outcome" value={filters.outcome} onChange={set('outcome')}>
          <option value="">Any</option>
          {OUTCOMES.map((o) => <option key={o} value={o}>{o}</option>)}
        </FormField>
        <FormField label="From" type="datetime-local" id="from" value={filters.from} onChange={set('from')} />
        <FormField label="To" type="datetime-local" id="to" value={filters.to} onChange={set('to')} />
        <div className="button-row">
          <button type="submit" className="button">Search</button>
          <button type="button" className="button secondary" onClick={() => { setFilters(EMPTY_FILTERS); setPage(0); }}>
            Reset
          </button>
        </div>
      </form>

      <Alert>{error}</Alert>

      {pageData === null && !error && <p className="muted">Loading the audit log…</p>}
      {pageData?.content.length === 0 && <p className="muted">No events match those filters.</p>}

      {pageData?.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">When</th>
                  <th scope="col">Actor</th>
                  <th scope="col">Action</th>
                  <th scope="col">Outcome</th>
                  <th scope="col">Account</th>
                  <th scope="col" className="num">Amount</th>
                  <th scope="col">Details</th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((event) => (
                  <tr key={event.auditId}>
                    <td>{formatDateTime(event.timestamp)}</td>
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
