import { useState } from 'react';
import { useUsers } from '../hooks/useUsers';
import { UserTable } from '../components/users/UserTable';
import { RoleChangeModal } from '../components/users/RoleChangeModal';
import { ManagerAssignModal } from '../components/users/ManagerAssignModal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { useToast } from '../components/common/Toast';
import type { UserProfile } from '../types/user';
import type { Role } from '../types/auth';

export function UserManagementPage() {
  const { addToast } = useToast();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<Role | ''>('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Modal state
  const [roleChangeUser, setRoleChangeUser] = useState<UserProfile | null>(null);
  const [managerAssignUser, setManagerAssignUser] = useState<UserProfile | null>(null);

  const { data, isLoading, error, refetch, changeRole, assignManager, deactivateUser } =
    useUsers({
      page,
      size: 20,
      search: search || undefined,
      role: roleFilter || undefined,
    });

  const handleRoleChange = async (userId: string, role: Role) => {
    setIsSubmitting(true);
    try {
      await changeRole(userId, role);
      addToast('Role updated successfully', 'success');
      setRoleChangeUser(null);
    } catch {
      addToast('Failed to update role', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleManagerAssign = async (userId: string, managerId: string) => {
    setIsSubmitting(true);
    try {
      await assignManager(userId, managerId);
      addToast('Manager assigned successfully', 'success');
      setManagerAssignUser(null);
    } catch {
      addToast('Failed to assign manager', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeactivate = async (user: UserProfile) => {
    if (
      !window.confirm(
        `Are you sure you want to deactivate ${user.firstName} ${user.lastName}?`
      )
    ) {
      return;
    }

    setIsSubmitting(true);
    try {
      await deactivateUser(user.id);
      addToast('User deactivated', 'success');
    } catch {
      addToast('Failed to deactivate user', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const users = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">User Management</h1>
        <p className="mt-1 text-sm text-gray-500">
          Manage users, roles, and manager assignments.
        </p>
      </div>

      {/* Filters */}
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <svg
            className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z"
            />
          </svg>
          <input
            type="text"
            placeholder="Search by name or email..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="w-full rounded-lg border border-gray-300 py-2 pl-9 pr-4 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <select
          value={roleFilter}
          onChange={(e) => {
            setRoleFilter(e.target.value as Role | '');
            setPage(0);
          }}
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
        >
          <option value="">All Roles</option>
          <option value="EMPLOYEE">Employee</option>
          <option value="MANAGER">Manager</option>
          <option value="ADMIN">Admin</option>
        </select>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex h-64 items-center justify-center">
          <LoadingSpinner size="lg" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center">
          <p className="text-sm text-red-600">{error}</p>
          <button
            type="button"
            onClick={refetch}
            className="mt-3 text-sm font-medium text-red-700 underline hover:text-red-800"
          >
            Try again
          </button>
        </div>
      ) : (
        <>
          <UserTable
            users={users}
            onChangeRole={setRoleChangeUser}
            onAssignManager={setManagerAssignUser}
            onDeactivate={handleDeactivate}
          />

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-gray-600">
                Page {page + 1} of {totalPages} ({data?.totalElements} total)
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                >
                  Previous
                </button>
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {/* Modals */}
      <RoleChangeModal
        isOpen={roleChangeUser !== null}
        user={roleChangeUser}
        onClose={() => setRoleChangeUser(null)}
        onConfirm={handleRoleChange}
        isSubmitting={isSubmitting}
      />

      <ManagerAssignModal
        isOpen={managerAssignUser !== null}
        user={managerAssignUser}
        onClose={() => setManagerAssignUser(null)}
        onConfirm={handleManagerAssign}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}
