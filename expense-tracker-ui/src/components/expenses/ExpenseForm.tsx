import { useState, type FormEvent } from 'react';
import type { Category } from '../../types/category';
import type { CreateExpenseRequest } from '../../types/expense';

interface ExpenseFormProps {
  initialValues?: Partial<CreateExpenseRequest>;
  categories: Category[];
  categoriesLoading: boolean;
  rejectionComment?: string | null;
  isSubmitting: boolean;
  onSaveDraft: (data: CreateExpenseRequest) => void;
  onSubmit: (data: CreateExpenseRequest) => void;
  onCancel: () => void;
}

interface FormErrors {
  amount?: string;
  description?: string;
  merchant?: string;
  expenseDate?: string;
  categoryId?: string;
}

export function ExpenseForm({
  initialValues,
  categories,
  categoriesLoading,
  rejectionComment,
  isSubmitting,
  onSaveDraft,
  onSubmit,
  onCancel,
}: ExpenseFormProps) {
  const [amount, setAmount] = useState(
    initialValues?.amount?.toString() ?? ''
  );
  const [description, setDescription] = useState(
    initialValues?.description ?? ''
  );
  const [merchant, setMerchant] = useState(initialValues?.merchant ?? '');
  const [expenseDate, setExpenseDate] = useState(
    initialValues?.expenseDate ?? ''
  );
  const [categoryId, setCategoryId] = useState(
    initialValues?.categoryId ?? ''
  );
  const [notes, setNotes] = useState(initialValues?.notes ?? '');
  const [errors, setErrors] = useState<FormErrors>({});

  const validate = (): FormErrors => {
    const errs: FormErrors = {};
    const parsedAmount = parseFloat(amount);

    if (!amount || isNaN(parsedAmount) || parsedAmount <= 0) {
      errs.amount = 'Amount must be a positive number.';
    }
    if (!description.trim()) {
      errs.description = 'Description is required.';
    }
    if (!merchant.trim()) {
      errs.merchant = 'Merchant is required.';
    }
    if (!expenseDate) {
      errs.expenseDate = 'Date is required.';
    }
    if (!categoryId) {
      errs.categoryId = 'Category is required.';
    }

    return errs;
  };

  const buildData = (): CreateExpenseRequest => ({
    amount: parseFloat(amount),
    description: description.trim(),
    merchant: merchant.trim(),
    expenseDate,
    categoryId,
    notes: notes.trim() || undefined,
  });

  const handleSaveDraft = (e: FormEvent) => {
    e.preventDefault();
    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }
    setErrors({});
    onSaveDraft(buildData());
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }
    setErrors({});
    onSubmit(buildData());
  };

  return (
    <form className="space-y-6">
      {rejectionComment && (
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
                Expense Rejected
              </h3>
              <p className="mt-1 text-sm text-red-700">{rejectionComment}</p>
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <div>
          <label
            htmlFor="amount"
            className="block text-sm font-medium text-gray-700"
          >
            Amount <span className="text-red-500">*</span>
          </label>
          <div className="relative mt-1 rounded-md shadow-sm">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
              <span className="text-gray-500 sm:text-sm">$</span>
            </div>
            <input
              id="amount"
              type="number"
              step="0.01"
              min="0"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className={`block w-full rounded-md pl-7 pr-3 py-2 text-sm border shadow-sm focus:outline-none focus:ring-1 ${
                errors.amount
                  ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
                  : 'border-gray-300 focus:border-indigo-500 focus:ring-indigo-500'
              }`}
              placeholder="0.00"
            />
          </div>
          {errors.amount && (
            <p className="mt-1 text-sm text-red-600">{errors.amount}</p>
          )}
        </div>

        <div>
          <label
            htmlFor="categoryId"
            className="block text-sm font-medium text-gray-700"
          >
            Category <span className="text-red-500">*</span>
          </label>
          <select
            id="categoryId"
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            disabled={categoriesLoading}
            className={`mt-1 block w-full rounded-md py-2 pl-3 pr-10 text-sm border shadow-sm focus:outline-none focus:ring-1 ${
              errors.categoryId
                ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
                : 'border-gray-300 focus:border-indigo-500 focus:ring-indigo-500'
            }`}
          >
            <option value="">Select a category</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
          {errors.categoryId && (
            <p className="mt-1 text-sm text-red-600">{errors.categoryId}</p>
          )}
        </div>

        <div>
          <label
            htmlFor="merchant"
            className="block text-sm font-medium text-gray-700"
          >
            Merchant <span className="text-red-500">*</span>
          </label>
          <input
            id="merchant"
            type="text"
            value={merchant}
            onChange={(e) => setMerchant(e.target.value)}
            className={`mt-1 block w-full rounded-md py-2 px-3 text-sm border shadow-sm focus:outline-none focus:ring-1 ${
              errors.merchant
                ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
                : 'border-gray-300 focus:border-indigo-500 focus:ring-indigo-500'
            }`}
            placeholder="e.g., Amazon, Uber"
          />
          {errors.merchant && (
            <p className="mt-1 text-sm text-red-600">{errors.merchant}</p>
          )}
        </div>

        <div>
          <label
            htmlFor="expenseDate"
            className="block text-sm font-medium text-gray-700"
          >
            Date <span className="text-red-500">*</span>
          </label>
          <input
            id="expenseDate"
            type="date"
            value={expenseDate}
            onChange={(e) => setExpenseDate(e.target.value)}
            className={`mt-1 block w-full rounded-md py-2 px-3 text-sm border shadow-sm focus:outline-none focus:ring-1 ${
              errors.expenseDate
                ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
                : 'border-gray-300 focus:border-indigo-500 focus:ring-indigo-500'
            }`}
          />
          {errors.expenseDate && (
            <p className="mt-1 text-sm text-red-600">{errors.expenseDate}</p>
          )}
        </div>
      </div>

      <div>
        <label
          htmlFor="description"
          className="block text-sm font-medium text-gray-700"
        >
          Description <span className="text-red-500">*</span>
        </label>
        <input
          id="description"
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          className={`mt-1 block w-full rounded-md py-2 px-3 text-sm border shadow-sm focus:outline-none focus:ring-1 ${
            errors.description
              ? 'border-red-300 focus:border-red-500 focus:ring-red-500'
              : 'border-gray-300 focus:border-indigo-500 focus:ring-indigo-500'
          }`}
          placeholder="Brief description of the expense"
        />
        {errors.description && (
          <p className="mt-1 text-sm text-red-600">{errors.description}</p>
        )}
      </div>

      <div>
        <label
          htmlFor="notes"
          className="block text-sm font-medium text-gray-700"
        >
          Notes
        </label>
        <textarea
          id="notes"
          rows={3}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          className="mt-1 block w-full rounded-md border-gray-300 py-2 px-3 text-sm border shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          placeholder="Additional notes (optional)"
        />
      </div>

      <div className="flex items-center justify-end gap-3 border-t border-gray-200 pt-6">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
          className="rounded-md bg-white px-4 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={handleSaveDraft}
          disabled={isSubmitting}
          className="rounded-md bg-white px-4 py-2 text-sm font-semibold text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 disabled:opacity-50"
        >
          {isSubmitting ? 'Saving...' : 'Save Draft'}
        </button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={isSubmitting}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50"
        >
          {isSubmitting ? 'Submitting...' : 'Submit'}
        </button>
      </div>
    </form>
  );
}
