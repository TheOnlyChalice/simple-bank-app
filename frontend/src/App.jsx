import { Route, Routes } from 'react-router';
import RequireAuth from './auth/RequireAuth';
import Layout from './components/Layout';
import AccountDetailsPage from './pages/AccountDetailsPage';
import CreateAccountPage from './pages/CreateAccountPage';
import DepositPage from './pages/DepositPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import OpenAccountPage from './pages/OpenAccountPage';
import ProfilePage from './pages/ProfilePage';
import TransactionsPage from './pages/TransactionsPage';
import TransferPage from './pages/TransferPage';
import WithdrawPage from './pages/WithdrawPage';
import AdminAccountsPage from './pages/admin/AdminAccountsPage';
import AdminAuditPage from './pages/admin/AdminAuditPage';
import AdminUserDetailsPage from './pages/admin/AdminUserDetailsPage';
import AdminUsersPage from './pages/admin/AdminUsersPage';

/** Which page shows for which address. Pages inside RequireAuth need a login. */
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="create-account" element={<CreateAccountPage />} />

        <Route element={<RequireAuth />}>
          <Route path="profile" element={<ProfilePage />} />
          <Route path="transfer" element={<TransferPage />} />
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
  );
}

