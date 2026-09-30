// Formatting shared by every page.

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function formatMoney(value) {
  return currency.format(Number(value ?? 0));
}

/** Timestamps without a zone (from LocalDateTime) are shown as local time. */
export function formatDateTime(value) {
  if (!value) return '';
  return new Date(value).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatDate(value) {
  if (!value) return '';
  return new Date(value).toLocaleDateString('en-US', { dateStyle: 'medium' });
}

export function formatAccountType(type) {
  return type === 'CHECKING' ? 'Checking' : 'Savings';
}

/** Adds up balances in whole cents, so the total is exact. */
export function totalBalance(accounts) {
  const cents = accounts.reduce((sum, account) => sum + Math.round(Number(account.balance) * 100), 0);
  return cents / 100;
}

export function describeTransaction(txn) {
  switch (txn.type) {
    case 'DEPOSIT':
      return 'Deposit';
    case 'WITHDRAW':
      return 'Withdrawal';
    case 'TRANSFER_IN':
      return `Transfer from #${txn.relatedAccountId}`;
    case 'TRANSFER_OUT':
      return `Transfer to #${txn.relatedAccountId}`;
    default:
      return txn.type;
  }
}

export function isMoneyIn(txn) {
  return txn.type === 'DEPOSIT' || txn.type === 'TRANSFER_IN';
}
