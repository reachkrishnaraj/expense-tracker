import { useState, useEffect, useCallback } from 'react';
import { expenseApi } from '../api/expenseApi';
import type { Expense, ExpenseFilterParams } from '../types/expense';

interface UseExpensesResult {
  expenses: Expense[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useExpenses(params: ExpenseFilterParams): UseExpensesResult {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const serializedParams = JSON.stringify(params);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    const parsedParams: ExpenseFilterParams = JSON.parse(serializedParams);

    expenseApi
      .getExpenses(parsedParams)
      .then((data) => {
        if (!cancelled) {
          setExpenses(data.content);
          setTotalElements(data.totalElements);
          setTotalPages(data.totalPages);
          setCurrentPage(data.page);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : 'Failed to load expenses'
          );
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [serializedParams, refreshKey]);

  const refetch = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  return {
    expenses,
    totalElements,
    totalPages,
    currentPage,
    isLoading,
    error,
    refetch,
  };
}
