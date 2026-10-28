import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './routes/ProtectedRoute';
import DashboardLayout from './layouts/DashboardLayout';
import SuperAdminLayout from './layouts/SuperAdminLayout';
import SuperAdminLoginPage from './pages/superadmin/SuperAdminLoginPage';
import { LoadingState } from './components/common/States';
import LoginPage from './pages/auth/LoginPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import ResetPasswordPage from './pages/auth/ResetPasswordPage';
import DashboardPage from './pages/DashboardPage';
import NotFoundPage from './pages/NotFoundPage';

const lazyPage = (loader) => lazy(loader);
const PublicPayPage = lazyPage(() => import('./pages/PublicPayPage'));
const HostelsPage = lazyPage(() => import('./pages/hostels/HostelsPage'));
const HostelSetupPage = lazyPage(() => import('./pages/hostels/HostelSetupPage'));
const RoomsPage = lazyPage(() => import('./pages/RoomsPage'));
const ResidentsPage = lazyPage(() => import('./pages/residents/ResidentsPage'));
const ResidentProfilePage = lazyPage(() => import('./pages/residents/ResidentProfilePage'));
const ResidentFormPage = lazyPage(() => import('./pages/residents/ResidentFormPage'));
const EnquiriesPage = lazyPage(() => import('./pages/EnquiriesPage'));
const VacanciesPage = lazyPage(() => import('./pages/VacanciesPage'));
const PaymentsPage = lazyPage(() => import('./pages/payments/PaymentsPage'));
const CollectPaymentPage = lazyPage(() => import('./pages/payments/CollectPaymentPage'));
const PaymentDetailPage = lazyPage(() => import('./pages/payments/PaymentDetailPage'));
const InvoiceDetailPage = lazyPage(() => import('./pages/payments/InvoiceDetailPage'));
const ExpensesPage = lazyPage(() => import('./pages/payments/ExpensesPage'));
const MaintenancePage = lazyPage(() => import('./pages/MaintenancePage'));
const StaffPage = lazyPage(() => import('./pages/StaffPage'));
const NotificationsPage = lazyPage(() => import('./pages/NotificationsPage'));
const AnnouncementsPage =lazyPage(() => import('./pages/AnnouncementsPage'));
const SettingsPage = lazyPage(() => import('./pages/SettingsPage'));
const ProfilePage = lazyPage(() => import('./pages/auth/ProfilePage'));
const ManagersPage = lazyPage(() => import('./pages/managers/ManagersPage'));
const ManagerFormPage = lazyPage(() => import('./pages/managers/ManagerFormPage'));
const ManagerDetailPage = lazyPage(() => import('./pages/managers/ManagerDetailPage'));
const SaPlansPage = lazyPage(() => import('./pages/superadmin/SaPlansPage'));
const SaDashboardPage = lazyPage(() => import('./pages/superadmin/SaDashboardPage'));
const SaOwnersPage = lazyPage(() => import('./pages/superadmin/SaOwnersPage'));
const SaOwnerFormPage = lazyPage(() => import('./pages/superadmin/SaOwnerFormPage'));
const SaOwnerDetailPage = lazyPage(() => import('./pages/superadmin/SaOwnerDetailPage'));
const SaHostelsPage = lazyPage(() => import('./pages/superadmin/SaHostelsPage'));
const SaReportsPage = lazyPage(() => import('./pages/superadmin/SaReportsPage'));
const SaActivityPage = lazyPage(() => import('./pages/superadmin/SaActivityPage'));
const SaSettingsPage = lazyPage(() => import('./pages/superadmin/SaSettingsPage'));

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/pay/:token" element={<Suspense fallback={<LoadingState />}><PublicPayPage /></Suspense>} />
      <Route path="/super-admin/login" element={<SuperAdminLoginPage />} />
      <Route path="/super-admin" element={<ProtectedRoute superAdmin><SuperAdminLayout /></ProtectedRoute>}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="*" element={
          <Suspense fallback={<LoadingState />}>
            <Routes>
              <Route path="dashboard" element={<SaDashboardPage />} />
              <Route path="owners" element={<SaOwnersPage />} />
              <Route path="owners/new" element={<SaOwnerFormPage />} />
              <Route path="owners/:ownerId" element={<SaOwnerDetailPage />} />
              <Route path="owners/:ownerId/edit" element={<SaOwnerFormPage />} />
              <Route path="plans" element={<SaPlansPage />} />
              <Route path="hostels" element={<SaHostelsPage />} />
              <Route path="reports" element={<SaReportsPage />} />
              <Route path="activity" element={<SaActivityPage />} />
              <Route path="settings" element={<SaSettingsPage />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </Suspense>
        } />
      </Route>
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route element={<ProtectedRoute><DashboardLayout /></ProtectedRoute>}>
        <Route index element={<DashboardPage />} />
        <Route path="*" element={
          <Suspense fallback={<LoadingState />}>
            <Routes>
              <Route path="hostels" element={<ProtectedRoute perm="HOSTEL_VIEW"><HostelsPage /></ProtectedRoute>} />
              <Route path="hostels/:id" element={<ProtectedRoute perm="HOSTEL_VIEW"><HostelSetupPage /></ProtectedRoute>} />
              <Route path="rooms" element={<RoomsPage />} />
              <Route path="residents" element={<ProtectedRoute perm="RESIDENT_VIEW"><ResidentsPage /></ProtectedRoute>} />
              <Route path="residents/new" element={<ProtectedRoute perm="RESIDENT_MANAGE"><ResidentFormPage /></ProtectedRoute>} />
              <Route path="residents/:id" element={<ProtectedRoute perm="RESIDENT_VIEW"><ResidentProfilePage /></ProtectedRoute>} />
              <Route path="residents/:id/edit" element={<ProtectedRoute perm="RESIDENT_MANAGE"><ResidentFormPage /></ProtectedRoute>} />
              <Route path="enquiries" element={<ProtectedRoute perm="ENQUIRY_MANAGE"><EnquiriesPage /></ProtectedRoute>} />
              <Route path="vacancies" element={<VacanciesPage />} />
              <Route path="payments" element={<ProtectedRoute perm="PAYMENT_VIEW"><PaymentsPage /></ProtectedRoute>} />
              <Route path="payments/collect" element={<ProtectedRoute perm="PAYMENT_COLLECT"><CollectPaymentPage /></ProtectedRoute>} />
              <Route path="payments/:id" element={<ProtectedRoute perm="PAYMENT_VIEW"><PaymentDetailPage /></ProtectedRoute>} />
              <Route path="invoices/:id" element={<ProtectedRoute perm="PAYMENT_VIEW"><InvoiceDetailPage /></ProtectedRoute>} />
              <Route path="expenses" element={<ProtectedRoute perm="EXPENSE_MANAGE"><ExpensesPage /></ProtectedRoute>} />
              <Route path="maintenance" element={<ProtectedRoute perm="MAINTENANCE_MANAGE"><MaintenancePage /></ProtectedRoute>} />
              <Route path="managers" element={<ProtectedRoute ownerOnly><ManagersPage /></ProtectedRoute>} />
              <Route path="managers/new" element={<Navigate to="/managers" replace />} />
              <Route path="managers/:managerId" element={<ProtectedRoute ownerOnly><ManagerDetailPage /></ProtectedRoute>} />
              <Route path="managers/:managerId/edit" element={<ProtectedRoute ownerOnly><ManagerFormPage /></ProtectedRoute>} />
              <Route path="staff" element={<ProtectedRoute perm="STAFF_MANAGE"><StaffPage /></ProtectedRoute>} />
              <Route path="notifications" element={<NotificationsPage />} />
              <Route path="announcements" element={<AnnouncementsPage />} />
              <Route path="settings" element={<SettingsPage />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="dashboard" element={<Navigate to="/" replace />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </Suspense>
        } />
      </Route>
    </Routes>
  );
}
