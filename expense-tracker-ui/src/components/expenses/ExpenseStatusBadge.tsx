import type { ExpenseStatus } from '../../types/expense';

interface ExpenseStatusBadgeProps {
  status: ExpenseStatus;
}

const statusConfig: Record<ExpenseStatus, { label: string; classes: string }> = {
  DRAFT: {
    label: 'Draft',
    classes: 'bg-gray-100 text-gray-700 ring-gray-500/10',
  },
  SUBMITTED: {
    label: 'Submitted',
    classes: 'bg-blue-50 text-blue-700 ring-blue-700/10',
  },
  APPROVED: {
    label: 'Approved',
    classes: 'bg-green-50 text-green-700 ring-green-600/20',
  },
  REJECTED: {
    label: 'Rejected',
    classes: 'bg-red-50 text-red-700 ring-red-600/10',
  },
};

export function ExpenseStatusBadge({ status }: ExpenseStatusBadgeProps) {
  const config = statusConfig[status];

  return (
    <span
      className={`inline-flex items-center rounded-md px-2 py-1 text-xs font-medium ring-1 ring-inset ${config.classes}`}
    >
      {config.label}
    </span>
  );
}
