import { useLanguage } from '../i18n/LanguageContext';
import { describeTransaction, formatDateTime, formatMoney, isMoneyIn, localeFor } from './format';

/** The transaction ledger (section 7.6): ID, type, amount, and date. */
export default function TransactionTable({ transactions, caption }) {
  const { t, language } = useLanguage();

  if (transactions.length === 0) {
    return <p className="muted">{t('transactionTable.empty')}</p>;
  }
  return (
    <div className="table-wrap">
      <table className="ledger">
        {caption && <caption>{caption}</caption>}
        <thead>
          <tr>
            <th scope="col">{t('transactionTable.id')}</th>
            <th scope="col">{t('transactionTable.type')}</th>
            <th scope="col" className="num">{t('transactionTable.amount')}</th>
            <th scope="col">{t('transactionTable.date')}</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((txn) => (
            <tr key={txn.txnId}>
              <td>#{txn.txnId}</td>
              <td>{describeTransaction(txn, t)}</td>
              <td className={`num ${isMoneyIn(txn) ? 'credit' : 'debit'}`}>
                {isMoneyIn(txn) ? '+' : '−'}
                {formatMoney(txn.amount)}
              </td>
              <td>{formatDateTime(txn.date, localeFor(language))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

