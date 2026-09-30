import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { auth } from '../api/bank';
import { setToken, setUnauthorizedHandler } from '../api/client';

// Who is logged in, shared with every page. The session (token, expiry, user) is kept
// in localStorage so a page refresh doesn't log you out, and removed on logout or expiry.

const STORAGE_KEY = 'simpleBank.session';
const AuthContext = createContext(null);

function readSavedSession() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (saved?.accessToken && new Date(saved.expiresAt) > new Date()) {
      setToken(saved.accessToken);
      return saved;
    }
  } catch {
    // Unreadable data: start logged out
  }
  localStorage.removeItem(STORAGE_KEY);
  return null;
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readSavedSession);
  const [sessionExpired, setSessionExpired] = useState(false);

  const startSession = useCallback((tokenResponse) => {
    const next = {
      accessToken: tokenResponse.accessToken,
      expiresAt: tokenResponse.expiresAt,
      user: tokenResponse.user,
    };
    setToken(next.accessToken);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setSessionExpired(false);
    setSession(next);
    return next;
  }, []);

  const logout = useCallback((expired = false) => {
    setToken(null);
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
    setSessionExpired(expired === true);
  }, []);

  // The backend answered 401 to a request with a token: the session is over
  useEffect(() => {
    setUnauthorizedHandler(() => logout(true));
  }, [logout]);

  // Log out automatically the moment the token expires
  useEffect(() => {
    if (!session) return undefined;
    const msLeft = new Date(session.expiresAt).getTime() - Date.now();
    const timer = setTimeout(() => logout(true), Math.max(msLeft, 0));
    return () => clearTimeout(timer);
  }, [session, logout]);

  const login = useCallback(
    async (email, password) => startSession(await auth.login(email, password)),
    [startSession],
  );

  const register = useCallback(
    async (details) => startSession(await auth.register(details)),
    [startSession],
  );

  /** After the user edits their profile, keep the saved copy in step. */
  const updateUser = useCallback((user) => {
    setSession((current) => {
      if (!current) return current;
      const next = { ...current, user };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  const value = useMemo(
    () => ({
      user: session?.user ?? null,
      isAdmin: session?.user?.role === 'ADMIN',
      sessionExpired,
      login,
      register,
      logout,
      updateUser,
    }),
    [session, sessionExpired, login, register, logout, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
