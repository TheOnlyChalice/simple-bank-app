import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { accounts } from '../../api/bank';
import { useLanguage } from '../../i18n/LanguageContext';
import Alert from '../../components/Alert';
import FormField from '../../components/FormField';
import Pagination from '../../components/Pagination';
import { formatAccountType, formatDate, formatMoney, localeFor } from '../../components/format';

const EMPTY_FILTERS = { minBalance: '', maxBalance: '', accountType: '' };
const PAGE_SIZE = 20;

/** Staff-only: search every account, or list accounts at or above a balance threshold. */
export default function AdminAccountsPage() {
  const { t, language } = useLanguage();
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
      <h1>{t('adminAccounts.title')}</h1>

      <div className="button-row">
        <button type="button" className={`button small ${premium ? 'secondary' : ''}`} onClick={() => togglePremium(false)}>
          {t('adminAccounts.all')}
        </button>
        <button type="button" className={`button small ${premium ? '' : 'secondary'}`} onClick={() => togglePremium(true)}>
          {t('adminAccounts.premium')}
        </button>
      </div>

      {!premium && (
        <form className="panel filters" onSubmit={handleSearch}>
          <FormField label={t('adminAccounts.minBalance')} type="number" id="minBalance" value={filters.minBalance}
            onChange={(e) => setFilters((c) => ({ ...c, minBalance: e.target.value }))} />
          <FormField label={t('adminAccounts.maxBalance')} type="number" id="maxBalance" value={filters.maxBalance}
            onChange={(e) => setFilters((c) => ({ ...c, maxBalance: e.target.value }))} />
          <FormField label={t('adminAccounts.accountType')} as="select" id="accountType" value={filters.accountType}
            onChange={(e) => setFilters((c) => ({ ...c, accountType: e.target.value }))}>
            <option value="">{t('adminAccounts.any')}</option>
            <option value="SAVINGS">{t('adminAccounts.savings')}</option>
            <option value="CHECKING">{t('adminAccounts.checking')}</option>
          </FormField>
          <div className="button-row">
            <button type="submit" className="button">{t('adminAccounts.search')}</button>
            <button type="button" className="button secondary" onClick={() => { setFilters(EMPTY_FILTERS); setPage(0); }}>
              {t('adminAccounts.reset')}
            </button>
          </div>
        </form>
      )}

      {premium && (
        <form className="panel filters" onSubmit={handleSearch}>
          <FormField label={t('adminAccounts.threshold')} type="number" id="threshold" value={threshold}
            onChange={(e) => setThreshold(e.target.value)} />
          <button type="submit" className="button">{t('adminAccounts.search')}</button>
        </form>
      )}

      <Alert>{error}</Alert>

      {pageData === null && !error && <p className="muted">{t('adminAccounts.loading')}</p>}
      {pageData?.content.length === 0 && <p className="muted">{t('adminAccounts.empty')}</p>}

      {pageData?.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  <th scope="col">{t('adminAccounts.account')}</th>
                  <th scope="col">{t('adminAccounts.owner')}</th>
                  <th scope="col">{t('adminAccounts.opened')}</th>
                  <th scope="col" className="num">{t('adminAccounts.balance')}</th>
                  <th scope="col"><span className="visually-hidden">{t('adminAccounts.view')}</span></th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((account) => (
                  <tr key={account.accountId}>
                    <td>{formatAccountType(account.accountType, t)} #{account.accountId}</td>
                    <td>{account.userName}</td>
                    <td>{formatDate(account.createdAt, localeFor(language))}</td>
                    <td className="num">{formatMoney(account.balance)}</td>
                    <td className="actions"><Link to={`/accounts/${account.accountId}`}>{t('adminAccounts.view')}</Link></td>
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

