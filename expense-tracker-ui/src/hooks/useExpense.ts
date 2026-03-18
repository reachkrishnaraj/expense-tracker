import { useState, useEffect, useCallback } from 'react';
import { expenseApi } from '../api/expenseApi';
import type { ExpenseDetail } from '../types/expense';

interface UseExpenseResult {
  expense: ExpenseDetail | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useExpense(id: string | undefined): UseExpenseResult {
  const [expense, setExpense] = useState<ExpenseDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (!id) {
      setIsLoading(false);
      return;
    }

    let cancelled = false;
    setIsLoading(true);
    setError(null);

    expenseApi
      .getExpense(id)
      .then((data) => {
        if (!cancelled) {
          setExpense(data);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : 'Failed to load expense'
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
  }, [id, refreshKey]);

  const refetch = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  return { expense, isLoading, error, refetch };
}
