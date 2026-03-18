import axiosInstance from './axiosInstance';
import type { PaginatedResponse } from '../types/common';
import type { UserProfile, Category } from '../types/user';
import type { Role } from '../types/auth';

export interface UserParams {
  page?: number;
  size?: number;
  search?: string;
  role?: Role | '';
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

export const userApi = {
  getUsers(params?: UserParams): Promise<PaginatedResponse<UserProfile>> {
    return axiosInstance
      .get<PaginatedResponse<UserProfile>>('/users', { params })
      .then((res) => res.data);
  },

  getManagers(): Promise<UserProfile[]> {
    return axiosInstance
      .get<UserProfile[]>('/users/managers')
      .then((res) => res.data);
  },

  changeRole(id: string, role: Role): Promise<UserProfile> {
    return axiosInstance
      .patch<UserProfile>(`/users/${id}/role`, { role })
      .then((res) => res.data);
  },

  assignManager(id: string, managerId: string): Promise<UserProfile> {
    return axiosInstance
      .patch<UserProfile>(`/users/${id}/manager`, { managerId })
      .then((res) => res.data);
  },

  deactivateUser(id: string): Promise<void> {
    return axiosInstance
      .patch(`/users/${id}/deactivate`)
      .then(() => undefined);
  },

  getCategories(): Promise<Category[]> {
    return axiosInstance
      .get<Category[]>('/categories')
      .then((res) => res.data);
  },

  addCategory(name: string): Promise<Category> {
    return axiosInstance
      .post<Category>('/categories', { name })
      .then((res) => res.data);
  },

  renameCategory(id: string, name: string): Promise<Category> {
    return axiosInstance
      .patch<Category>(`/categories/${id}`, { name })
      .then((res) => res.data);
  },

  deactivateCategory(id: string): Promise<void> {
    return axiosInstance
      .patch(`/categories/${id}/deactivate`)
      .then(() => undefined);
  },

  activateCategory(id: string): Promise<void> {
    return axiosInstance
      .patch(`/categories/${id}/activate`)
      .then(() => undefined);
  },
};
