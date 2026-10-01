import { Suspense, lazy } from 'react';
import { Route, Routes } from 'react-router';
import RequireAuth from './auth/RequireAuth';
import Layout from './components/Layout';
import Spinner from './components/Spinner';
import { useLanguage } from './i18n/LanguageContext';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';

// Lazy-loaded: not needed for the first paint, so customers and staff only download
// the pages they actually visit (e.g. a customer never downloads the admin pages).
const AccountDetailsPage = lazy(() => import('./pages/AccountDetailsPage'));
const CreateAccountPage = lazy(() => import('./pages/CreateAccountPage'));
const DepositPage = lazy(() => import('./pages/DepositPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));
const OpenAccountPage = lazy(() => import('./pages/OpenAccountPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const TransactionsPage = lazy(() => import('./pages/TransactionsPage'));
const TransferPage = lazy(() => import('./pages/TransferPage'));
const ScheduledTransfersPage = lazy(() => import('./pages/ScheduledTransfersPage'));
const WithdrawPage = lazy(() => import('./pages/WithdrawPage'));
const AdminAccountsPage = lazy(() => import('./pages/admin/AdminAccountsPage'));
const AdminAuditPage = lazy(() => import('./pages/admin/AdminAuditPage'));
const AdminUserDetailsPage = lazy(() => import('./pages/admin/AdminUserDetailsPage'));
const AdminUsersPage = lazy(() => import('./pages/admin/AdminUsersPage'));

/** Which page shows for which address. Pages inside RequireAuth need a login. */
export default function App() {
  const { t } = useLanguage();
  return (
    <Suspense fallback={<Spinner label={t('app.loading')} />}>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="create-account" element={<CreateAccountPage />} />

          <Route element={<RequireAuth />}>
            <Route path="profile" element={<ProfilePage />} />
            <Route path="transfer" element={<TransferPage />} />
            <Route path="transfers/scheduled" element={<ScheduledTransfersPage />} />
            <Route path="accounts/new" element={<OpenAccountPage />} />
            <Route path="accounts/:accountId" element={<AccountDetailsPage />} />
            <Route path="accounts/:accountId/deposit" element={<DepositPage />} />
            <Route path="accounts/:accountId/withdraw" element={<WithdrawPage />} />
            <Route path="accounts/:accountId/transactions" element={<TransactionsPage />} />
          </Route>

          <Route element={<RequireAuth staffOnly />}>
            <Route path="admin/users" element={<AdminUsersPage />} />
            <Route path="admin/users/:userId" element={<AdminUserDetailsPage />} />
            <Route path="admin/accounts" element={<AdminAccountsPage />} />
            <Route path="admin/audit" element={<AdminAuditPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}

