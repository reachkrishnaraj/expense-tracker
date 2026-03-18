import { useState, useEffect, useCallback } from 'react';
import { userApi, type UserParams } from '../api/userApi';
import type { PaginatedResponse } from '../types/common';
import type { UserProfile, Category } from '../types/user';
import type { Role } from '../types/auth';

interface UseUsersReturn {
  data: PaginatedResponse<UserProfile> | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
  changeRole: (id: string, role: Role) => Promise<void>;
  assignManager: (id: string, managerId: string) => Promise<void>;
  deactivateUser: (id: string) => Promise<void>;
}

export function useUsers(params?: UserParams): UseUsersReturn {
  const [data, setData] = useState<PaginatedResponse<UserProfile> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await userApi.getUsers(params);
      setData(result);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to load users';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [params?.page, params?.size, params?.search, params?.role, params?.sortBy, params?.sortDir]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const changeRole = useCallback(
    async (id: string, role: Role) => {
      await userApi.changeRole(id, role);
      await fetchData();
    },
    [fetchData]
  );

  const assignManager = useCallback(
    async (id: string, managerId: string) => {
      await userApi.assignManager(id, managerId);
      await fetchData();
    },
    [fetchData]
  );

  const deactivateUser = useCallback(
    async (id: string) => {
      await userApi.deactivateUser(id);
      await fetchData();
    },
    [fetchData]
  );

  return { data, isLoading, error, refetch: fetchData, changeRole, assignManager, deactivateUser };
}

interface UseCategoriesReturn {
  categories: Category[];
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
  addCategory: (name: string) => Promise<void>;
  renameCategory: (id: string, name: string) => Promise<void>;
  deactivateCategory: (id: string) => Promise<void>;
  activateCategory: (id: string) => Promise<void>;
}

export function useCategories(): UseCategoriesReturn {
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await userApi.getCategories();
      setCategories(result);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to load categories';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const addCategory = useCallback(
    async (name: string) => {
      await userApi.addCategory(name);
      await fetchData();
    },
    [fetchData]
  );

  const renameCategory = useCallback(
    async (id: string, name: string) => {
      await userApi.renameCategory(id, name);
      await fetchData();
    },
    [fetchData]
  );

  const deactivateCategory = useCallback(
    async (id: string) => {
      await userApi.deactivateCategory(id);
      await fetchData();
    },
    [fetchData]
  );

  const activateCategory = useCallback(
    async (id: string) => {
      await userApi.activateCategory(id);
      await fetchData();
    },
    [fetchData]
  );

  return {
    categories,
    isLoading,
    error,
    refetch: fetchData,
    addCategory,
    renameCategory,
    deactivateCategory,
    activateCategory,
  };
}
