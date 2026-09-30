// Every backend endpoint the app uses, grouped by resource.
import { apiFetch } from './client';

export const auth = {
  register: (details) => apiFetch('/api/auth/register', { method: 'POST', body: details }),
  login: (email, password) => apiFetch('/api/auth/login', { method: 'POST', body: { email, password } }),
  me: () => apiFetch('/api/auth/me'),
};

export const users = {
  get: (userId) => apiFetch(`/api/users/${userId}`),
  accounts: (userId) => apiFetch(`/api/users/${userId}/accounts`),
  update: (userId, details) => apiFetch(`/api/users/${userId}`, { method: 'PUT', body: details }),
  search: (filters) => apiFetch('/api/users', { query: filters }), // staff only
};

export const accounts = {
  create: (userId, accountType) => apiFetch('/api/accounts', { method: 'POST', body: { userId, accountType } }),
  get: (accountId) => apiFetch(`/api/accounts/${accountId}`),
  remove: (accountId) => apiFetch(`/api/accounts/${accountId}`, { method: 'DELETE' }),
  deposit: (accountId, amount) =>
    apiFetch(`/api/accounts/${accountId}/deposit`, { method: 'POST', body: { amount: toAmount(amount) } }),
  withdraw: (accountId, amount) =>
    apiFetch(`/api/accounts/${accountId}/withdraw`, { method: 'POST', body: { amount: toAmount(amount) } }),
  transactions: (accountId, page = 0, size = 10) =>
    apiFetch(`/api/accounts/${accountId}/transactions`, { query: { page, size } }),
  transfer: (fromAccountId, toAccountId, amount) =>
    apiFetch('/api/transfers', {
      method: 'POST',
      body: { fromAccountId: Number(fromAccountId), toAccountId: Number(toAccountId), amount: toAmount(amount) },
    }),
  search: (filters) => apiFetch('/api/accounts', { query: filters }), // staff only
  premium: (threshold, page = 0, size = 20) =>
    apiFetch('/api/accounts/premium', { query: { threshold, page, size } }), // staff only
};

export const audit = {
  search: (filters) => apiFetch('/api/audit', { query: filters }), // staff only
};

/** An empty box is sent as null, so the backend answers "amount is required". */
function toAmount(value) {
  return value === '' || value === null || value === undefined ? null : Number(value);
}
