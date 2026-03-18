import { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useExpense } from '../hooks/useExpense';
import { useCategories } from '../hooks/useCategories';
import { expenseApi } from '../api/expenseApi';
import { receiptApi } from '../api/receiptApi';
import { ExpenseForm } from '../components/expenses/ExpenseForm';
import { ReceiptUpload } from '../components/expenses/ReceiptUpload';
import { ReceiptGallery } from '../components/expenses/ReceiptGallery';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useToast } from '../components/common/Toast';
import type { CreateExpenseRequest, Receipt } from '../types/expense';

export function ExpenseFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { addToast } = useToast();
  const isEditing = Boolean(id);

  const { expense, isLoading: expenseLoading } = useExpense(id);
  const { categories, isLoading: categoriesLoading } = useCategories();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [createdExpenseId, setCreatedExpenseId] = useState<string | null>(null);

  // Track receipts: use expense receipts when editing, local state for new
  const currentReceipts = isEditing ? (expense?.receipts ?? []) : receipts;
  const effectiveExpenseId = id ?? createdExpenseId;

  const handleSaveDraft = useCallback(
    async (data: CreateExpenseRequest) => {
      setIsSubmitting(true);
      try {
        if (isEditing && id) {
          await expenseApi.updateExpense(id, data);
          addToast('Expense saved as draft.', 'success');
          navigate('/expenses');
        } else {
          const created = await expenseApi.createExpense(data);
          setCreatedExpenseId(created.id);
          addToast('Expense saved as draft.', 'success');
          navigate('/expenses');
        }
      } catch {
        addToast('Failed to save expense. Please try again.', 'error');
      } finally {
        setIsSubmitting(false);
      }
    },
    [isEditing, id, navigate, addToast]
  );

  const handleSubmit = useCallback(
    async (data: CreateExpenseRequest) => {
      setIsSubmitting(true);
      try {
        let expenseId: string;
        if (isEditing && id) {
          await expenseApi.updateExpense(id, data);
          expenseId = id;
        } else {
          const created = await expenseApi.createExpense(data);
          expenseId = created.id;
        }
        await expenseApi.submitExpense(expenseId);
        addToast('Expense submitted for approval.', 'success');
        navigate('/expenses');
      } catch {
        addToast('Failed to submit expense. Please try again.', 'error');
      } finally {
        setIsSubmitting(false);
      }
    },
    [isEditing, id, navigate, addToast]
  );

  const handleReceiptUpload = useCallback(
    async (files: File[]) => {
      if (!effectiveExpenseId) {
        addToast(
          'Please save the expense first before uploading receipts.',
          'info'
        );
        return;
      }
      for (const file of files) {
        try {
          const receipt = await receiptApi.uploadReceipt(
            effectiveExpenseId,
            file
          );
          setReceipts((prev) => [...prev, receipt]);
          addToast(`Receipt "${file.name}" uploaded.`, 'success');
        } catch {
          addToast(`Failed to upload "${file.name}".`, 'error');
        }
      }
    },
    [effectiveExpenseId, addToast]
  );

  const handleReceiptDelete = useCallback(
    async (receiptId: string) => {
      if (!effectiveExpenseId) return;
      try {
        await receiptApi.deleteReceipt(effectiveExpenseId, receiptId);
        setReceipts((prev) => prev.filter((r) => r.id !== receiptId));
        addToast('Receipt removed.', 'success');
      } catch {
        addToast('Failed to remove receipt.', 'error');
      }
    },
    [effectiveExpenseId, addToast]
  );

  if (isEditing && expenseLoading) {
    return <LoadingSpinner size="lg" className="py-12" />;
  }

  if (isEditing && !expense) {
    return (
      <div className="rounded-md bg-red-50 p-4">
        <p className="text-sm text-red-700">Expense not found.</p>
      </div>
    );
  }

  const canEdit =
    !isEditing || expense?.status === 'DRAFT' || expense?.status === 'REJECTED';

  if (isEditing && !canEdit) {
    return (
      <div className="rounded-md bg-yellow-50 p-4">
        <p className="text-sm text-yellow-700">
          This expense cannot be edited in its current status.
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <button
          onClick={() => navigate(-1)}
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
          Back
        </button>
        <h1 className="text-2xl font-bold text-gray-900">
          {isEditing ? 'Edit Expense' : 'New Expense'}
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          {isEditing
            ? 'Update the expense details below.'
            : 'Fill in the details to create a new expense.'}
        </p>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <ExpenseForm
          initialValues={
            expense
              ? {
                  amount: expense.amount,
                  description: expense.description,
                  merchant: expense.merchant,
                  expenseDate: expense.expenseDate,
                  categoryId: expense.categoryId,
                  notes: expense.notes ?? undefined,
                }
              : undefined
          }
          categories={categories}
          categoriesLoading={categoriesLoading}
          rejectionComment={expense?.rejectionComment}
          isSubmitting={isSubmitting}
          onSaveDraft={handleSaveDraft}
          onSubmit={handleSubmit}
          onCancel={() => navigate(-1)}
        />
      </div>

      {/* Receipt section */}
      <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900">Receipts</h2>
        <p className="mt-1 text-sm text-gray-500">
          Upload up to 3 receipt files (max 5MB each).
          {!effectiveExpenseId &&
            ' Save the expense first to attach receipts.'}
        </p>

        <div className="mt-4 space-y-4">
          {currentReceipts.length > 0 && effectiveExpenseId && (
            <ReceiptGallery
              expenseId={effectiveExpenseId}
              receipts={currentReceipts}
              canDelete
              onDelete={handleReceiptDelete}
            />
          )}

          <ReceiptUpload
            existingCount={currentReceipts.length}
            onUpload={handleReceiptUpload}
            disabled={!effectiveExpenseId}
          />
        </div>
      </div>
    </div>
  );
}
