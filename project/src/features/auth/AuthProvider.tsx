import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '@/api/authService';
import type { User } from '@/api/types';
import { clearToken, getToken, saveToken } from '@/utils/authStorage';

interface AuthContextValue {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(() => getToken());

  const meQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    enabled: Boolean(token),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const resetSession = useCallback(() => {
    clearToken();
    setToken(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    const handler = () => resetSession();
    window.addEventListener('auth:unauthorized', handler);
    return () => window.removeEventListener('auth:unauthorized', handler);
  }, [resetSession]);

  useEffect(() => {
    if (token && meQuery.isError) {
      resetSession();
    }
  }, [token, meQuery.isError, resetSession]);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await loginRequest(email, password);
      saveToken(response.accessToken);
      setToken(response.accessToken);
      queryClient.setQueryData(['auth', 'me'], response.user);
      return response.user;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } catch {
      // The local session must be cleared even if the server is unreachable.
    } finally {
      resetSession();
    }
  }, [resetSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: meQuery.data ?? null,
      token,
      isLoading: Boolean(token) && meQuery.isLoading,
      isAuthenticated: Boolean(token) && Boolean(meQuery.data),
      login,
      logout,
    }),
    [meQuery.data, meQuery.isLoading, token, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
