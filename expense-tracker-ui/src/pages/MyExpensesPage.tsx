import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useExpenses } from '../hooks/useExpenses';
import { useCategories } from '../hooks/useCategories';
import { ExpenseTable } from '../components/expenses/ExpenseTable';
import {
  ExpenseFilterBar,
  type FilterValues,
} from '../components/expenses/ExpenseFilterBar';
import { Pagination } from '../components/common/Pagination';
import { EmptyState } from '../components/common/EmptyState';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import type { ExpenseFilterParams } from '../types/expense';

const PAGE_SIZE = 10;

export function MyExpensesPage() {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<FilterValues>({
    status: '',
    categoryId: '',
    startDate: '',
    endDate: '',
  });
  const [page, setPage] = useState(0);
  const [sortConfig, setSortConfig] = useState({
    field: 'expenseDate',
    direction: 'desc' as 'asc' | 'desc',
  });

  const { categories } = useCategories();

  const params: ExpenseFilterParams = {
    page,
    size: PAGE_SIZE,
    sortBy: sortConfig.field,
    sortDir: sortConfig.direction,
    ...(filters.status && { status: filters.status as ExpenseFilterParams['status'] }),
    ...(filters.categoryId && { categoryId: filters.categoryId }),
    ...(filters.startDate && { startDate: filters.startDate }),
    ...(filters.endDate && { endDate: filters.endDate }),
  };

  const { expenses, totalElements, totalPages, isLoading, error } =
    useExpenses(params);

  const handleFilterChange = useCallback((newFilters: FilterValues) => {
    setFilters(newFilters);
    setPage(0);
  }, []);

  const handleSort = useCallback(
    (field: string) => {
      setSortConfig((prev) => ({
        field,
        direction:
          prev.field === field && prev.direction === 'asc' ? 'desc' : 'asc',
      }));
      setPage(0);
    },
    []
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Expenses</h1>
          <p className="mt-1 text-sm text-gray-500">
            Track and manage your expense reports.
          </p>
        </div>
        <button
          onClick={() => navigate('/expenses/new')}
          className="inline-flex items-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >
          <svg
            className="-ml-0.5 mr-1.5 h-5 w-5"
            viewBox="0 0 20 20"
            fill="currentColor"
          >
            <path d="M10.75 4.75a.75.75 0 00-1.5 0v4.5h-4.5a.75.75 0 000 1.5h4.5v4.5a.75.75 0 001.5 0v-4.5h4.5a.75.75 0 000-1.5h-4.5v-4.5z" />
          </svg>
          New Expense
        </button>
      </div>

      <ExpenseFilterBar
        filters={filters}
        categories={categories}
        onFilterChange={handleFilterChange}
      />

      {isLoading ? (
        <LoadingSpinner size="lg" className="py-12" />
      ) : error ? (
        <div className="rounded-md bg-red-50 p-4">
          <p className="text-sm text-red-700">{error}</p>
        </div>
      ) : expenses.length === 0 ? (
        <EmptyState
          title="No expenses found"
          description="Get started by creating your first expense report."
          action={{
            label: 'New Expense',
            onClick: () => navigate('/expenses/new'),
          }}
        />
      ) : (
        <>
          <ExpenseTable
            expenses={expenses}
            sortConfig={sortConfig}
            onSort={handleSort}
          />
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={PAGE_SIZE}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
