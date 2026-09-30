import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { accounts } from '../../api/bank';
import Alert from '../../components/Alert';
import FormField from '../../components/FormField';
import Pagination from '../../components/Pagination';
import { formatAccountType, formatDate, formatMoney } from '../../components/format';

const EMPTY_FILTERS = { minBalance: '', maxBalance: '', accountType: '' };
const PAGE_SIZE = 20;

/** Staff-only: search every account, or list accounts at or above a balance threshold. */
export default function AdminAccountsPage() {
  const [premium, setPremium] = useState(false);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [threshold, setThreshold] = useState('1000');
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    const request = premium
      ? accounts.premium(threshold || 0, page, PAGE_SIZE)
      : accounts.search({ ...filters, page, size: PAGE_SIZE });
    request
      .then((data) => { if (!ignore) setPageData(data); })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [premium, filters, threshold, page]);

  function handleSearch(event) {
    event.preventDefault();
    setPage(0);
  }

  function togglePremium(next) {
    setPremium(next);
    setPage(0);
    setError('');
  }

  return (
    <div className="stack-lg">
      <h1>Accounts</h1>

      <div className="button-row">
        <button type="button" className={`button small ${premium ? 'secondary' : ''}`} onClick={() => togglePremium(false)}>
          All accounts
        </button>
        <button type="button" className={`button small ${premium ? '' : 'secondary'}`} onClick={() => togglePremium(true)}>
          Premium accounts
        </button>
      </div>

      {!premium && (
        <form className="panel filters" onSubmit={handleSearch}>
          <FormField label="Min balance" type="number" id="minBalance" value={filters.minBalance}
            onChange={(e) => setFilters((c) => ({ ...c, minBalance: e.target.value }))} />
          <FormField label="Max balance" type="number" id="maxBalance" value={filters.maxBalance}
            onChange={(e) => setFilters((c) => ({ ...c, maxBalance: e.target.value }))} />
          <FormField label="Account type" as="select" id="accountType" value={filters.accountType}
            onChange={(e) => setFilters((c) => ({ ...c, accountType: e.target.value }))}>
            <option value="">Any</option>
            <option value="SAVINGS">Savings</option>
            <option value="CHECKING">Checking</option>
          </FormField>
          <div className="button-row">
            <button type="submit" className="button">Search</button>
            <button type="button" className="button secondary" onClick={() => { setFilters(EMPTY_FILTERS); setPage(0); }}>
              Reset
            </button>
          </div>
        </form>
      )}

      {premium && (
        <form className="panel filters" onSubmit={handleSearch}>
          <FormField label="Balance at or above" type="number" id="threshold" value={threshold}
            onChange={(e) => setThreshold(e.target.value)} />
          <button type="submit" className="button">Search</button>
        </form>
      )}

      <Alert>{error}</Alert>

      {pageData === null && !error && <p className="muted">Loading accounts…</p>}
      {pageData?.content.length === 0 && <p className="muted">No accounts match those filters.</p>}

      {pageData?.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">Account</th>
                  <th scope="col">Owner</th>
                  <th scope="col">Opened</th>
                  <th scope="col" className="num">Balance</th>
                  <th scope="col"><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((account) => (
                  <tr key={account.accountId}>
                    <td>{formatAccountType(account.accountType)} #{account.accountId}</td>
                    <td>{account.userName}</td>
                    <td>{formatDate(account.createdAt)}</td>
                    <td className="num">{formatMoney(account.balance)}</td>
                    <td className="actions"><Link to={`/accounts/${account.accountId}`}>View</Link></td>
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
