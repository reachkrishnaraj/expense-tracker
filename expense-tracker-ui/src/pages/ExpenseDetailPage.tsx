import { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useExpense } from '../hooks/useExpense';
import { expenseApi } from '../api/expenseApi';
import { receiptApi } from '../api/receiptApi';
import { ExpenseStatusBadge } from '../components/expenses/ExpenseStatusBadge';
import { ReceiptGallery } from '../components/expenses/ReceiptGallery';
import { AuditTimeline } from '../components/expenses/AuditTimeline';
import { ConfirmModal } from '../components/common/ConfirmModal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useToast } from '../components/common/Toast';
import { formatCurrency } from '../utils/formatCurrency';
import { formatDate } from '../utils/formatDate';

export function ExpenseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { addToast } = useToast();
  const { expense, isLoading, error, refetch } = useExpense(id);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showSubmitModal, setShowSubmitModal] = useState(false);
  const [isActionLoading, setIsActionLoading] = useState(false);

  const handleSubmit = useCallback(async () => {
    if (!id) return;
    setIsActionLoading(true);
    try {
      await expenseApi.submitExpense(id);
      addToast('Expense submitted for approval.', 'success');
      setShowSubmitModal(false);
      refetch();
    } catch {
      addToast('Failed to submit expense.', 'error');
    } finally {
      setIsActionLoading(false);
    }
  }, [id, addToast, refetch]);

  const handleDelete = useCallback(async () => {
    if (!id) return;
    setIsActionLoading(true);
    try {
      await expenseApi.deleteExpense(id);
      addToast('Expense deleted.', 'success');
      navigate('/expenses');
    } catch {
      addToast('Failed to delete expense.', 'error');
    } finally {
      setIsActionLoading(false);
      setShowDeleteModal(false);
    }
  }, [id, addToast, navigate]);

  const handleReceiptDelete = useCallback(
    async (receiptId: string) => {
      if (!id) return;
      try {
        await receiptApi.deleteReceipt(id, receiptId);
        addToast('Receipt removed.', 'success');
        refetch();
      } catch {
        addToast('Failed to remove receipt.', 'error');
      }
    },
    [id, addToast, refetch]
  );

  if (isLoading) {
    return <LoadingSpinner size="lg" className="py-12" />;
  }

  if (error || !expense) {
    return (
      <div className="rounded-md bg-red-50 p-4">
        <p className="text-sm text-red-700">
          {error ?? 'Expense not found.'}
        </p>
        <button
          onClick={() => navigate('/expenses')}
          className="mt-2 text-sm font-medium text-red-800 underline hover:text-red-900"
        >
          Back to expenses
        </button>
      </div>
    );
  }

  const canEdit =
    expense.status === 'DRAFT' || expense.status === 'REJECTED';
  const canSubmit = expense.status === 'DRAFT';
  const canDelete = expense.status === 'DRAFT';

  return (
    <div className="mx-auto max-w-4xl space-y-8">
      {/* Header */}
      <div>
        <button
          onClick={() => navigate('/expenses')}
          className="mb-4 inline-flex items-center text-sm text-gray-500 hover:text-gray-700"
        >
          <svg
            className="mr-1 h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M15.75 19.5L8.25 12l7.5-7.5"
            />
          </svg>
          Back to Expenses
        </button>

        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-gray-900">
                {expense.description}
              </h1>
              <ExpenseStatusBadge status={expense.status} />
            </div>
            <p className="mt-1 text-sm text-gray-500">
              Created on {formatDate(expense.createdAt)}
            </p>
          </div>

          <div className="flex items-center gap-2">
            {canEdit && (
              <button
                onClick={() => navigate(`/expenses/${expense.id}/edit`)}
                className="inline-flex items-center rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
              >
                Edit
              </button>
            )}
            {canSubmit && (
              <button
                onClick={() => setShowSubmitModal(true)}
                className="inline-flex items-center rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
              >
                Submit
              </button>
            )}
            {canDelete && (
              <button
                onClick={() => setShowDeleteModal(true)}
                className="inline-flex items-center rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-red-500"
              >
                Delete
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Rejection banner */}
      {expense.rejectionComment && (
        <div className="rounded-md bg-red-50 border border-red-200 p-4">
          <div className="flex">
            <svg
              className="h-5 w-5 text-red-400 flex-shrink-0"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fillRule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z"
                clipRule="evenodd"
              />
            </svg>
            <div className="ml-3">
              <h3 className="text-sm font-medium text-red-800">
                Rejection Reason
              </h3>
              <p className="mt-1 text-sm text-red-700">
                {expense.rejectionComment}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Expense Details */}
      <div className="rounded-lg border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-200 px-6 py-4">
          <h2 className="text-lg font-semibold text-gray-900">
            Expense Details
          </h2>
        </div>
        <dl className="divide-y divide-gray-200">
          <div className="grid grid-cols-3 gap-4 px-6 py-4">
            <dt className="text-sm font-medium text-gray-500">Amount</dt>
            <dd className="col-span-2 text-sm font-semibold text-gray-900">
              {formatCurrency(expense.amount)}
            </dd>
          </div>
          <div className="grid grid-cols-3 gap-4 px-6 py-4">
            <dt className="text-sm font-medium text-gray-500">Category</dt>
            <dd className="col-span-2 text-sm text-gray-900">
              {expense.categoryName}
            </dd>
          </div>
          <div className="grid grid-cols-3 gap-4 px-6 py-4">
            <dt className="text-sm font-medium text-gray-500">Merchant</dt>
            <dd className="col-span-2 text-sm text-gray-900">
              {expense.merchant}
            </dd>
          </div>
          <div className="grid grid-cols-3 gap-4 px-6 py-4">
            <dt className="text-sm font-medium text-gray-500">Date</dt>
            <dd className="col-span-2 text-sm text-gray-900">
              {formatDate(expense.expenseDate)}
            </dd>
          </div>
          {expense.notes && (
            <div className="grid grid-cols-3 gap-4 px-6 py-4">
              <dt className="text-sm font-medium text-gray-500">Notes</dt>
              <dd className="col-span-2 text-sm text-gray-900 whitespace-pre-wrap">
                {expense.notes}
              </dd>
            </div>
          )}
        </dl>
      </div>

      {/* Receipts */}
      <div className="rounded-lg border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-200 px-6 py-4">
          <h2 className="text-lg font-semibold text-gray-900">Receipts</h2>
        </div>
        <div className="p-6">
          <ReceiptGallery
            expenseId={expense.id}
            receipts={expense.receipts}
            canDelete={canEdit}
            onDelete={handleReceiptDelete}
          />
        </div>
      </div>

      {/* Audit Timeline */}
      <div className="rounded-lg border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-200 px-6 py-4">
          <h2 className="text-lg font-semibold text-gray-900">Activity</h2>
        </div>
        <div className="p-6">
          <AuditTimeline entries={expense.auditTrail} />
        </div>
      </div>

      {/* Confirm Submit Modal */}
      <ConfirmModal
        open={showSubmitModal}
        title="Submit Expense"
        message="Are you sure you want to submit this expense for approval? You will not be able to edit it until it is reviewed."
        confirmLabel={isActionLoading ? 'Submitting...' : 'Submit'}
        variant="primary"
        onConfirm={handleSubmit}
        onCancel={() => setShowSubmitModal(false)}
      />

      {/* Confirm Delete Modal */}
      <ConfirmModal
        open={showDeleteModal}
        title="Delete Expense"
        message="Are you sure you want to delete this expense? This action cannot be undone."
        confirmLabel={isActionLoading ? 'Deleting...' : 'Delete'}
        variant="danger"
        onConfirm={handleDelete}
        onCancel={() => setShowDeleteModal(false)}
      />
    </div>
  );
}
