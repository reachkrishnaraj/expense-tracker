import {
  createContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { setAccessToken } from '../api/axiosInstance';
import type { User, LoginRequest, RegisterRequest, Role } from '../types/auth';

export interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | null>(null);

function getRoleHomePath(role: Role): string {
  switch (role) {
    case 'ADMIN':
      return '/dashboard';
    case 'MANAGER':
      return '/approvals';
    case 'EMPLOYEE':
    default:
      return '/expenses';
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();

  const isAuthenticated = user !== null;

  // On mount: attempt to restore session from refresh token
  useEffect(() => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      setIsLoading(false);
      return;
    }

    authApi
      .refreshToken(refreshToken)
      .then((response) => {
        setAccessToken(response.accessToken);
        localStorage.setItem('refreshToken', response.refreshToken);
        setUser(response.user);
      })
      .catch(() => {
        // Token invalid or expired, clear stored data
        localStorage.removeItem('refreshToken');
        setAccessToken(null);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  const login = useCallback(
    async (data: LoginRequest) => {
      const response = await authApi.login(data);
      setAccessToken(response.accessToken);
      localStorage.setItem('refreshToken', response.refreshToken);
      setUser(response.user);
      navigate(getRoleHomePath(response.user.role));
    },
    [navigate]
  );

  const register = useCallback(
    async (data: RegisterRequest) => {
      const response = await authApi.register(data);
      setAccessToken(response.accessToken);
      localStorage.setItem('refreshToken', response.refreshToken);
      setUser(response.user);
      navigate(getRoleHomePath(response.user.role));
    },
    [navigate]
  );

  const logout = useCallback(async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    try {
      if (refreshToken) {
        await authApi.logout(refreshToken);
      }
    } catch {
      // Proceed with local cleanup even if server logout fails
    } finally {
      setAccessToken(null);
      localStorage.removeItem('refreshToken');
      setUser(null);
      navigate('/login');
    }
  }, [navigate]);

  return (
    <AuthContext.Provider
      value={{ user, isAuthenticated, isLoading, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export { getRoleHomePath };
