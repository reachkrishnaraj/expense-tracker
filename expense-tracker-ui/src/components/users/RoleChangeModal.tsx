import { useState } from 'react';
import type { UserProfile } from '../../types/user';
import type { Role } from '../../types/auth';

interface RoleChangeModalProps {
  isOpen: boolean;
  user: UserProfile | null;
  onClose: () => void;
  onConfirm: (userId: string, role: Role) => void;
  isSubmitting?: boolean;
}

const roles: { value: Role; label: string }[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'ADMIN', label: 'Admin' },
];

export function RoleChangeModal({
  isOpen,
  user,
  onClose,
  onConfirm,
  isSubmitting = false,
}: RoleChangeModalProps) {
  const [selectedRole, setSelectedRole] = useState<Role | ''>('');

  if (!isOpen || !user) return null;

  const currentRole = user.role;
  const effectiveRole = selectedRole || currentRole;

  const handleSubmit = () => {
    if (effectiveRole && effectiveRole !== currentRole) {
      onConfirm(user.id, effectiveRole);
      setSelectedRole('');
    }
  };

  const handleClose = () => {
    setSelectedRole('');
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div
        className="fixed inset-0 bg-black/50 transition-opacity"
        onClick={handleClose}
      />
      <div className="relative z-10 w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-gray-900">Change Role</h3>
        <p className="mt-1 text-sm text-gray-500">
          Change role for{' '}
          <span className="font-medium text-gray-700">
            {user.firstName} {user.lastName}
          </span>
        </p>

        <div className="mt-4">
          <label
            htmlFor="role-select"
            className="block text-sm font-medium text-gray-700"
          >
            New Role
          </label>
          <select
            id="role-select"
            value={effectiveRole}
            onChange={(e) => setSelectedRole(e.target.value as Role)}
            className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          >
            {roles.map((role) => (
              <option key={role.value} value={role.value}>
                {role.label}
                {role.value === currentRole ? ' (current)' : ''}
              </option>
            ))}
          </select>
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
            disabled={isSubmitting || effectiveRole === currentRole}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {isSubmitting ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  );
}
