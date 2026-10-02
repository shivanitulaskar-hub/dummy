import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import * as authApi from '../api/auth';
import { registerAuthHandlers } from '../api/client';
import type { LoginResponse, UserInfo } from '../api/types';
import { tokenStore } from './tokenStore';

interface AuthState {
  user: UserInfo | null;
  /** True while an existing token is being validated at startup. */
  initializing: boolean;
  login: (username: string, password: string) => Promise<UserInfo>;
  logout: () => void;
  /** Applies a fresh token/user (e.g. after a password change). */
  applyLogin: (response: LoginResponse) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [initializing, setInitializing] = useState(() => tokenStore.get() !== null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const applyLogin = useCallback((response: LoginResponse) => {
    tokenStore.set(response.accessToken);
    setUser(response.user);
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
    queryClient.clear();
    navigate('/login', { replace: true });
  }, [navigate, queryClient]);

  const login = useCallback(
    async (username: string, password: string) => {
      const response = await authApi.login(username, password);
      applyLogin(response);
      return response.user;
    },
    [applyLogin],
  );

  useEffect(() => {
    registerAuthHandlers({
      unauthorized: logout,
      passwordChangeRequired: () => navigate('/change-password', { replace: true }),
    });
  }, [logout, navigate]);

  // Validate a token left over from before a page refresh.
  useEffect(() => {
    if (!tokenStore.get()) {
      return;
    }
    authApi
      .fetchMe()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setInitializing(false));
  }, []);

  const value = useMemo(
    () => ({ user, initializing, login, logout, applyLogin }),
    [user, initializing, login, logout, applyLogin],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return ctx;
}
