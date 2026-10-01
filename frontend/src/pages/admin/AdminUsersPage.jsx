import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { users } from '../../api/bank';
import { useLanguage } from '../../i18n/LanguageContext';
import Alert from '../../components/Alert';
import FormField from '../../components/FormField';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { formatDate, localeFor } from '../../components/format';

const EMPTY_FILTERS = { state: '', city: '', zip: '', minBalance: '', maxBalance: '', balanceMode: 'TOTAL' };
const PAGE_SIZE = 20;

/** Staff-only: search and browse every customer. */
export default function AdminUsersPage() {
  const { t, language } = useLanguage();
  const [draft, setDraft] = useState(EMPTY_FILTERS);
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
    return (event) => setDraft((current) => ({ ...current, [name]: event.target.value }));
  }

  function handleSearch(event) {
    event.preventDefault();
    setFilters(draft);
    setPage(0);
  }

  function handleReset() {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
    setPage(0);
  }

  return (
    <div className="stack-lg">
      <h1>{t('adminUsers.title')}</h1>

      <form className="panel filters" onSubmit={handleSearch}>
        <FormField label={t('adminUsers.state')} maxLength={2} id="state" value={draft.state} onChange={handleFilterChange('state')} />
        <FormField label={t('adminUsers.city')} id="city" value={draft.city} onChange={handleFilterChange('city')} />
        <FormField label={t('adminUsers.zip')} id="zip" value={draft.zip} onChange={handleFilterChange('zip')} />
        <FormField label={t('adminUsers.minBalance')} type="number" id="minBalance" value={draft.minBalance}
          onChange={handleFilterChange('minBalance')} />
        <FormField label={t('adminUsers.maxBalance')} type="number" id="maxBalance" value={draft.maxBalance}
          onChange={handleFilterChange('maxBalance')} />
        <FormField label={t('adminUsers.balanceMode')} as="select" id="balanceMode" value={draft.balanceMode}
          onChange={handleFilterChange('balanceMode')}>
          <option value="TOTAL">{t('adminUsers.balanceMode.total')}</option>
          <option value="ANY_ACCOUNT">{t('adminUsers.balanceMode.any')}</option>
        </FormField>
        <div className="button-row">
          <button type="submit" className="button">{t('adminUsers.search')}</button>
          <button type="button" className="button secondary" onClick={handleReset}>{t('adminUsers.reset')}</button>
        </div>
      </form>

      <Alert>{error}</Alert>

      {pageData === null && !error && <Spinner label={t('adminUsers.loading')} />}

      {pageData?.content.length === 0 && <p className="muted">{t('adminUsers.empty')}</p>}

      {pageData?.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">{t('adminUsers.name')}</th>
                  <th scope="col">{t('adminUsers.email')}</th>
                  <th scope="col">{t('adminUsers.address')}</th>
                  <th scope="col">{t('adminUsers.since')}</th>
                  <th scope="col"><span className="visually-hidden">{t('adminUsers.view')}</span></th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((u) => (
                  <tr key={u.userId}>
                    <td>{u.name}</td>
                    <td>{u.email}</td>
                    <td>{u.address ? `${u.address.city}, ${u.address.state}` : '—'}</td>
                    <td>{formatDate(u.createdAt, localeFor(language))}</td>
                    <td className="actions"><Link to={`/admin/users/${u.userId}`}>{t('adminUsers.view')}</Link></td>
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

