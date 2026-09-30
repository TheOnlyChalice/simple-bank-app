import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { users } from '../../api/bank';
import Alert from '../../components/Alert';
import FormField from '../../components/FormField';
import Pagination from '../../components/Pagination';
import { formatDate } from '../../components/format';

const EMPTY_FILTERS = { state: '', city: '', zip: '', minBalance: '', maxBalance: '', balanceMode: 'TOTAL' };
const PAGE_SIZE = 20;

/** Staff-only: search and browse every customer. */
export default function AdminUsersPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    users
      .search({ ...filters, page, size: PAGE_SIZE })
      .then((data) => { if (!ignore) setPageData(data); })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [filters, page]);

  function handleFilterChange(name) {
    return (event) => setFilters((current) => ({ ...current, [name]: event.target.value }));
  }

  function handleSearch(event) {
    event.preventDefault();
    setPage(0);
  }

  function handleReset() {
    setFilters(EMPTY_FILTERS);
    setPage(0);
  }

  return (
    <div className="stack-lg">
      <h1>Customers</h1>

      <form className="panel filters" onSubmit={handleSearch}>
        <FormField label="State" maxLength={2} id="state" value={filters.state} onChange={handleFilterChange('state')} />
        <FormField label="City" id="city" value={filters.city} onChange={handleFilterChange('city')} />
        <FormField label="ZIP code" id="zip" value={filters.zip} onChange={handleFilterChange('zip')} />
        <FormField label="Min balance" type="number" id="minBalance" value={filters.minBalance}
          onChange={handleFilterChange('minBalance')} />
        <FormField label="Max balance" type="number" id="maxBalance" value={filters.maxBalance}
          onChange={handleFilterChange('maxBalance')} />
        <FormField label="Balance mode" as="select" id="balanceMode" value={filters.balanceMode}
          onChange={handleFilterChange('balanceMode')}>
          <option value="TOTAL">Total across accounts</option>
          <option value="ANY_ACCOUNT">Any single account</option>
        </FormField>
        <div className="button-row">
          <button type="submit" className="button">Search</button>
          <button type="button" className="button secondary" onClick={handleReset}>Reset</button>
        </div>
      </form>

      <Alert>{error}</Alert>

      {pageData === null && !error && <p className="muted">Loading customers…</p>}

      {pageData?.content.length === 0 && <p className="muted">No customers match those filters.</p>}

      {pageData?.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Email</th>
                  <th scope="col">Address</th>
                  <th scope="col">Since</th>
                  <th scope="col"><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((u) => (
                  <tr key={u.userId}>
                    <td>{u.name}</td>
                    <td>{u.email}</td>
                    <td>{u.address ? `${u.address.city}, ${u.address.state}` : '—'}</td>
                    <td>{formatDate(u.createdAt)}</td>
                    <td className="actions"><Link to={`/admin/users/${u.userId}`}>View</Link></td>
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
