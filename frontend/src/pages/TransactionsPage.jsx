import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { accounts } from '../api/bank';
import Alert from '../components/Alert';
import Pagination from '../components/Pagination';
import TransactionTable from '../components/TransactionTable';
import { formatAccountType } from '../components/format';

const PAGE_SIZE = 10;

/** The full, paginated transaction history for one account. */
export default function TransactionsPage() {
  const { accountId } = useParams();
  const [account, setAccount] = useState(null);
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    accounts
      .get(accountId)
      .then((loaded) => { if (!ignore) setAccount(loaded); })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [accountId]);

  useEffect(() => {
    let ignore = false;
    accounts
      .transactions(accountId, page, PAGE_SIZE)
      .then((data) => { if (!ignore) setPageData(data); })
      .catch((err) => { if (!ignore) setError(err.message); });
    return () => { ignore = true; };
  }, [accountId, page]);

  if (error) {
    return (
      <div className="narrow stack">
        <Alert>{error}</Alert>
        <Link to="/">Back to your accounts</Link>
      </div>
    );
  }

  return (
    <div className="stack-lg">
      <div className="page-header">
        <h1>Transactions</h1>
        <Link to={`/accounts/${accountId}`} className="button secondary">Back to account</Link>
      </div>
      {account && <p className="muted">{formatAccountType(account.accountType)} account #{account.accountId}</p>}

      {pageData === null && <p className="muted">Loading transactions…</p>}
      {pageData && (
        <>
          <TransactionTable transactions={pageData.content} />
          <Pagination
            page={pageData.page}
            totalPages={pageData.totalPages}
            first={pageData.first}
            last={pageData.last}
            onChange={setPage}
          />
        </>
      )}
    </div>
  );
}
