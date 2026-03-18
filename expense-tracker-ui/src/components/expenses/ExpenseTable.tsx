import { useNavigate } from 'react-router-dom';
import type { Expense } from '../../types/expense';
import { ExpenseStatusBadge } from './ExpenseStatusBadge';
import { formatCurrency } from '../../utils/formatCurrency';
import { formatDate } from '../../utils/formatDate';

interface SortConfig {
  field: string;
  direction: 'asc' | 'desc';
}

interface ExpenseTableProps {
  expenses: Expense[];
  sortConfig: SortConfig;
  onSort: (field: string) => void;
}

function SortIcon({
  field,
  sortConfig,
}: {
  field: string;
  sortConfig: SortConfig;
}) {
  if (sortConfig.field !== field) {
    return (
      <svg
        className="ml-1 inline h-4 w-4 text-gray-400"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"
        />
      </svg>
    );
  }

  return sortConfig.direction === 'asc' ? (
    <svg
      className="ml-1 inline h-4 w-4 text-indigo-600"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M5 15l7-7 7 7"
      />
    </svg>
  ) : (
    <svg
      className="ml-1 inline h-4 w-4 text-indigo-600"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M19 9l-7 7-7-7"
      />
    </svg>
  );
}

export function ExpenseTable({
  expenses,
  sortConfig,
  onSort,
}: ExpenseTableProps) {
  const navigate = useNavigate();

  const sortableHeader = (label: string, field: string) => (
    <th
      scope="col"
      className="cursor-pointer px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 hover:text-gray-700"
      onClick={() => onSort(field)}
    >
      {label}
      <SortIcon field={field} sortConfig={sortConfig} />
    </th>
  );

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200 shadow-sm">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            {sortableHeader('Date', 'expenseDate')}
            <th
              scope="col"
              className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500"
            >
              Description
            </th>
            <th
              scope="col"
              className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500"
            >
              Category
            </th>
            {sortableHeader('Amount', 'amount')}
            <th
              scope="col"
              className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500"
            >
              Status
            </th>
            <th scope="col" className="relative px-6 py-3">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 bg-white">
          {expenses.map((expense) => (
            <tr
              key={expense.id}
              className="cursor-pointer hover:bg-gray-50 transition-colors"
              onClick={() => navigate(`/expenses/${expense.id}`)}
            >
              <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900">
                {formatDate(expense.expenseDate)}
              </td>
              <td className="px-6 py-4 text-sm text-gray-900">
                <div className="max-w-xs truncate">{expense.description}</div>
                {expense.merchant && (
                  <div className="text-xs text-gray-500">{expense.merchant}</div>
                )}
              </td>
              <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                {expense.categoryName}
              </td>
              <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-gray-900">
                {formatCurrency(expense.amount)}
              </td>
              <td className="whitespace-nowrap px-6 py-4">
                <ExpenseStatusBadge status={expense.status} />
              </td>
              <td className="whitespace-nowrap px-6 py-4 text-right text-sm font-medium">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate(`/expenses/${expense.id}`);
                  }}
                  className="text-indigo-600 hover:text-indigo-900"
                >
                  View
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
