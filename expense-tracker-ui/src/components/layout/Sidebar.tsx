import { NavLink } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import type { Role } from '../../types/auth';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

interface NavItem {
  label: string;
  to: string;
  roles: Role[] | 'ALL';
}

const navItems: NavItem[] = [
  { label: 'Dashboard', to: '/dashboard', roles: ['ADMIN'] },
  { label: 'My Expenses', to: '/expenses', roles: ['EMPLOYEE', 'MANAGER'] },
  { label: 'New Expense', to: '/expenses/new', roles: ['EMPLOYEE', 'MANAGER'] },
  {
    label: 'Pending Approvals',
    to: '/approvals',
    roles: ['MANAGER', 'ADMIN'],
  },
  { label: 'Team Stats', to: '/team-stats', roles: ['MANAGER', 'ADMIN'] },
  { label: 'Users', to: '/users', roles: ['ADMIN'] },
  { label: 'Categories', to: '/categories', roles: ['ADMIN'] },
  { label: 'Profile', to: '/profile', roles: 'ALL' },
];

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  const { user } = useAuth();

  const visibleItems = navItems.filter((item) => {
    if (item.roles === 'ALL') return true;
    return user && item.roles.includes(user.role);
  });

  const linkClasses = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      isActive
        ? 'bg-indigo-600 text-white'
        : 'text-gray-300 hover:bg-gray-800 hover:text-white'
    }`;

  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={onClose}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-gray-900 transition-transform duration-300 lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Logo / Brand */}
        <div className="flex h-16 items-center gap-2 border-b border-gray-800 px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-sm font-bold text-white">
            ET
          </div>
          <span className="text-lg font-semibold text-white">
            Expense Tracker
          </span>
        </div>

        {/* Navigation */}
        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {visibleItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={linkClasses}
              onClick={onClose}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="border-t border-gray-800 px-6 py-4">
          <p className="text-xs text-gray-500">
            {user?.organizationName ?? 'Organization'}
          </p>
        </div>
      </aside>
    </>
  );
}
