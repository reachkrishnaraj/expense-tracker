import { useState, useEffect } from 'react';
import type { UserProfile } from '../../types/user';
import { userApi } from '../../api/userApi';

interface ManagerAssignModalProps {
  isOpen: boolean;
  user: UserProfile | null;
  onClose: () => void;
  onConfirm: (userId: string, managerId: string) => void;
  isSubmitting?: boolean;
}

export function ManagerAssignModal({
  isOpen,
  user,
  onClose,
  onConfirm,
  isSubmitting = false,
}: ManagerAssignModalProps) {
  const [selectedManagerId, setSelectedManagerId] = useState('');
  const [managers, setManagers] = useState<UserProfile[]>([]);
  const [loadingManagers, setLoadingManagers] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setLoadingManagers(true);
      userApi
        .getManagers()
        .then(setManagers)
        .catch(() => setManagers([]))
        .finally(() => setLoadingManagers(false));
    }
  }, [isOpen]);

  if (!isOpen || !user) return null;

  const handleSubmit = () => {
    if (selectedManagerId) {
      onConfirm(user.id, selectedManagerId);
      setSelectedManagerId('');
    }
  };

  const handleClose = () => {
    setSelectedManagerId('');
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div
        className="fixed inset-0 bg-black/50 transition-opacity"
        onClick={handleClose}
      />
      <div className="relative z-10 w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-gray-900">Assign Manager</h3>
        <p className="mt-1 text-sm text-gray-500">
          Assign a manager for{' '}
          <span className="font-medium text-gray-700">
            {user.firstName} {user.lastName}
          </span>
        </p>

        {user.managerName && (
          <p className="mt-2 text-xs text-gray-500">
            Current manager:{' '}
            <span className="font-medium text-gray-700">
              {user.managerName}
            </span>
          </p>
        )}

        <div className="mt-4">
          <label
            htmlFor="manager-select"
            className="block text-sm font-medium text-gray-700"
          >
            Manager
          </label>
          {loadingManagers ? (
            <div className="mt-1 flex items-center gap-2 text-sm text-gray-500">
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-indigo-600" />
              Loading managers...
            </div>
          ) : (
            <select
              id="manager-select"
              value={selectedManagerId}
              onChange={(e) => setSelectedManagerId(e.target.value)}
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            >
              <option value="">Select a manager</option>
              {managers
                .filter((m) => m.id !== user.id)
                .map((manager) => (
                  <option key={manager.id} value={manager.id}>
                    {manager.firstName} {manager.lastName} ({manager.email})
                  </option>
                ))}
            </select>
          )}
        </div>

        <div className="mt-6 flex justify-end gap-3">
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
            disabled={isSubmitting || !selectedManagerId}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {isSubmitting ? 'Assigning...' : 'Assign'}
          </button>
        </div>
      </div>
    </div>
  );
}
