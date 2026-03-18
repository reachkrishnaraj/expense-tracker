import axiosInstance from './axiosInstance';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth';

export const authApi = {
  login(data: LoginRequest): Promise<AuthResponse> {
    return axiosInstance
      .post<AuthResponse>('/auth/login', data)
      .then((res) => res.data);
  },

  register(data: RegisterRequest): Promise<AuthResponse> {
    return axiosInstance
      .post<AuthResponse>('/auth/register', data)
      .then((res) => res.data);
  },

  refreshToken(token: string): Promise<AuthResponse> {
    return axiosInstance
      .post<AuthResponse>('/auth/refresh', { refreshToken: token })
      .then((res) => res.data);
  },

  logout(token: string): Promise<void> {
    return axiosInstance
      .post('/auth/logout', { refreshToken: token })
      .then(() => undefined);
  },
};
