import { useNavigate } from 'react-router-dom';
import type { PendingExpense } from '../../types/approval';

interface ApprovalTableProps {
  expenses: PendingExpense[];
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
  onToggleAll: () => void;
  onApprove: (id: string) => void;
  onReject: (id: string) => void;
  isSubmitting: boolean;
}

export function ApprovalTable({
  expenses,
  selectedIds,
  onToggleSelect,
  onToggleAll,
  onApprove,
  onReject,
  isSubmitting,
}: ApprovalTableProps) {
  const navigate = useNavigate();
  const allSelected = expenses.length > 0 && selectedIds.size === expenses.length;
  const someSelected = selectedIds.size > 0 && selectedIds.size < expenses.length;

  const formatCurrency = (amount: number, currency: string) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
    }).format(amount);
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  if (expenses.length === 0) {
    return (
      <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-green-100">
          <svg className="h-6 w-6 text-green-600" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <h3 className="text-sm font-medium text-gray-900">All caught up!</h3>
        <p className="mt-1 text-sm text-gray-500">
          No expenses pending your approval.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="w-10 px-4 py-3">
                <input
                  type="checkbox"
                  checked={allSelected}
                  ref={(el) => {
                    if (el) el.indeterminate = someSelected;
                  }}
                  onChange={onToggleAll}
                  className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                />
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                Submitter
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                Date
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                Category
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                Description
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                Amount
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {expenses.map((expense) => (
              <tr
                key={expense.id}
                className="cursor-pointer hover:bg-gray-50 transition-colors"
                onClick={() => navigate(`/expenses/${expense.id}`)}
              >
                <td
                  className="px-4 py-3"
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    type="checkbox"
                    checked={selectedIds.has(expense.id)}
                    onChange={() => onToggleSelect(expense.id)}
                    className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                  />
                </td>
                <td className="whitespace-nowrap px-4 py-3">
                  <div className="text-sm font-medium text-gray-900">
                    {expense.submitterName}
                  </div>
                  <div className="text-xs text-gray-500">
                    {expense.submitterEmail}
                  </div>
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-600">
                  {formatDate(expense.date)}
                </td>
                <td className="whitespace-nowrap px-4 py-3">
                  <span className="inline-flex rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700">
                    {expense.category}
                  </span>
                </td>
                <td className="max-w-xs truncate px-4 py-3 text-sm text-gray-600">
                  {expense.description}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-right text-sm font-semibold text-gray-900">
                  {formatCurrency(expense.amount, expense.currency)}
                </td>
                <td
                  className="whitespace-nowrap px-4 py-3 text-right"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => onApprove(expense.id)}
                      disabled={isSubmitting}
                      className="rounded-md bg-green-50 px-3 py-1 text-xs font-medium text-green-700 hover:bg-green-100 disabled:opacity-50"
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      onClick={() => onReject(expense.id)}
                      disabled={isSubmitting}
                      className="rounded-md bg-red-50 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-50"
                    >
                      Reject
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
