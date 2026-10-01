// Formatting shared by every page.

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function formatMoney(value) {
  return currency.format(Number(value ?? 0));
}

/** Timestamps without a zone (from LocalDateTime) are shown as local time. */
export function formatDateTime(value, locale = 'en-US') {
  if (!value) return '';
  return new Date(value).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatDate(value, locale = 'en-US') {
  if (!value) return '';
  return new Date(value).toLocaleDateString(locale, { dateStyle: 'medium' });
}

export function formatAccountType(type, t) {
  const key = type === 'CHECKING' ? 'common.checking' : 'common.savings';
  return t ? t(key) : (type === 'CHECKING' ? 'Checking' : 'Savings');
}

/** Adds up balances in whole cents, so the total is exact. */
export function totalBalance(accounts) {
  const cents = accounts.reduce((sum, account) => sum + Math.round(Number(account.balance) * 100), 0);
  return cents / 100;
}

export function describeTransaction(txn, t) {
  switch (txn.type) {
    case 'DEPOSIT':
      return t('txn.deposit');
    case 'WITHDRAW':
      return t('txn.withdrawal');
    case 'TRANSFER_IN':
      return t('txn.transferFrom', { id: txn.relatedAccountId });
    case 'TRANSFER_OUT':
      return t('txn.transferTo', { id: txn.relatedAccountId });
    default:
      return txn.type;
  }
}

export function isMoneyIn(txn) {
  return txn.type === 'DEPOSIT' || txn.type === 'TRANSFER_IN';
}

/** 'es-US' for Spanish date formatting, 'en-US' otherwise. */
export function localeFor(language) {
  return language === 'es' ? 'es-US' : 'en-US';
}

