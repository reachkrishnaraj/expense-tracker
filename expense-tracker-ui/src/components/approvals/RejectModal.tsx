import { useState } from 'react';

interface RejectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
  title?: string;
  isSubmitting?: boolean;
}

export function RejectModal({
  isOpen,
  onClose,
  onConfirm,
  title = 'Reject Expense',
  isSubmitting = false,
}: RejectModalProps) {
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  if (!isOpen) return null;

  const isValid = reason.trim().length > 0;

  const handleSubmit = () => {
    setTouched(true);
    if (isValid) {
      onConfirm(reason.trim());
      setReason('');
      setTouched(false);
    }
  };

  const handleClose = () => {
    setReason('');
    setTouched(false);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div
        className="fixed inset-0 bg-black/50 transition-opacity"
        onClick={handleClose}
      />
      <div className="relative z-10 w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
        <p className="mt-1 text-sm text-gray-500">
          Please provide a reason for the rejection.
        </p>

        <div className="mt-4">
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            onBlur={() => setTouched(true)}
            placeholder="Enter rejection reason..."
            rows={4}
            className={`w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 ${
              touched && !isValid
                ? 'border-red-300 focus:ring-red-500'
                : 'border-gray-300 focus:ring-indigo-500'
            }`}
          />
          {touched && !isValid && (
            <p className="mt-1 text-xs text-red-600">
              Rejection reason is required.
            </p>
          )}
        </div>

        <div className="mt-4 flex justify-end gap-3">
          <button
            type="button"
            onClick={handleClose}
            disabled={isSubmitting}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
          >
            {isSubmitting ? 'Rejecting...' : 'Reject'}
          </button>
        </div>
      </div>
    </div>
  );
}
