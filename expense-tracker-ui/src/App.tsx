import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, getRoleHomePath } from './context/AuthContext';
import { ToastProvider } from './components/common/Toast';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { RoleGuard } from './components/auth/RoleGuard';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { MyExpensesPage } from './pages/MyExpensesPage';
import { ExpenseFormPage } from './pages/ExpenseFormPage';
import { ExpenseDetailPage } from './pages/ExpenseDetailPage';
import { PendingApprovalsPage } from './pages/PendingApprovalsPage';
import { TeamStatsPage } from './pages/TeamStatsPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { UserManagementPage } from './pages/UserManagementPage';
import { CategoryManagementPage } from './pages/CategoryManagementPage';
import { ProfilePage } from './pages/ProfilePage';
import { useAuth } from './hooks/useAuth';

function HomeRedirect() {
  const { user } = useAuth();
  const target = user ? getRoleHomePath(user.role) : '/login';
  return <Navigate to={target} replace />;
}

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Protected routes */}
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              {/* Employee + Manager routes */}
              <Route path="/expenses" element={<MyExpensesPage />} />
              <Route path="/expenses/new" element={<ExpenseFormPage />} />
              <Route path="/expenses/:id" element={<ExpenseDetailPage />} />
              <Route
                path="/expenses/:id/edit"
                element={<ExpenseFormPage />}
              />

              {/* Manager routes */}
              <Route
                path="/approvals"
                element={
                  <RoleGuard allowedRoles={['MANAGER', 'ADMIN']}>
                    <PendingApprovalsPage />
                  </RoleGuard>
                }
              />
              <Route
                path="/team-stats"
                element={
                  <RoleGuard allowedRoles={['MANAGER', 'ADMIN']}>
                    <TeamStatsPage />
                  </RoleGuard>
                }
              />

              {/* Admin routes */}
              <Route
                path="/dashboard"
                element={
                  <RoleGuard allowedRoles={['ADMIN']}>
                    <AdminDashboardPage />
                  </RoleGuard>
                }
              />
              <Route
                path="/users"
                element={
                  <RoleGuard allowedRoles={['ADMIN']}>
                    <UserManagementPage />
                  </RoleGuard>
                }
              />
              <Route
                path="/categories"
                element={
                  <RoleGuard allowedRoles={['ADMIN']}>
                    <CategoryManagementPage />
                  </RoleGuard>
                }
              />

              {/* Common */}
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/" element={<HomeRedirect />} />
            </Route>
          </Route>

          {/* 404 */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </ToastProvider>
  );
}

export default App;
