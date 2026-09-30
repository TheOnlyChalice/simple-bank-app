import { Route, Routes } from 'react-router';
import RequireAuth from './auth/RequireAuth';
import Layout from './components/Layout';
import AccountDetailsPage from './pages/AccountDetailsPage';
import CreateAccountPage from './pages/CreateAccountPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import OpenAccountPage from './pages/OpenAccountPage';

/** Which page shows for which address. Pages inside RequireAuth need a login. */
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="create-account" element={<CreateAccountPage />} />

        <Route element={<RequireAuth />}>
          <Route path="accounts/new" element={<OpenAccountPage />} />
          <Route path="accounts/:accountId" element={<AccountDetailsPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
