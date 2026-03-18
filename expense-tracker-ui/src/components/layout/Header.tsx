import { useAuth } from '../../hooks/useAuth';

interface HeaderProps {
  onMenuToggle: () => void;
}

const roleBadgeColors: Record<string, string> = {
  ADMIN: 'bg-red-100 text-red-800',
  MANAGER: 'bg-blue-100 text-blue-800',
  EMPLOYEE: 'bg-green-100 text-green-800',
};

export function Header({ onMenuToggle }: HeaderProps) {
  const { user, logout } = useAuth();

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-gray-200 bg-white px-4 lg:px-6">
      {/* Mobile menu button */}
      <button
        onClick={onMenuToggle}
        className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-gray-700 lg:hidden"
        aria-label="Toggle sidebar"
      >
        <svg
          className="h-6 w-6"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M4 6h16M4 12h16M4 18h16"
          />
        </svg>
      </button>

      {/* Spacer for desktop */}
      <div className="hidden lg:block" />

      {/* Right side: user info + logout */}
      <div className="flex items-center gap-4">
        {user && (
          <>
            <span className="hidden text-sm text-gray-500 sm:inline">
              {user.organizationName}
            </span>
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${roleBadgeColors[user.role] ?? 'bg-gray-100 text-gray-800'}`}
            >
              {user.role}
            </span>
            <span className="text-sm font-medium text-gray-700">
              {user.firstName} {user.lastName}
            </span>
          </>
        )}
        <button
          onClick={logout}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
        >
          Logout
        </button>
      </div>
    </header>
  );
}
