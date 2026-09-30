import { describeTransaction, formatDateTime, formatMoney, isMoneyIn } from './format';

/** The transaction ledger (section 7.6): ID, type, amount, and date. */
export default function TransactionTable({ transactions, caption }) {
  if (transactions.length === 0) {
    return <p className="muted">No transactions yet. Deposits, withdrawals, and transfers will appear here.</p>;
  }
  return (
    <div className="table-wrap">
      <table className="ledger">
        {caption && <caption>{caption}</caption>}
        <thead>
          <tr>
            <th scope="col">Transaction ID</th>
            <th scope="col">Type</th>
            <th scope="col" className="num">Amount</th>
            <th scope="col">Date</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((txn) => (
            <tr key={txn.txnId}>
              <td>#{txn.txnId}</td>
              <td>{describeTransaction(txn)}</td>
              <td className={`num ${isMoneyIn(txn) ? 'credit' : 'debit'}`}>
                {isMoneyIn(txn) ? '+' : '−'}
                {formatMoney(txn.amount)}
              </td>
              <td>{formatDateTime(txn.date)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
