import { lazy, Suspense } from 'react';
import { CircularProgress } from '@mui/material';
import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { AppLayout } from './components/AppLayout';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { LiveSessionsPage } from './pages/LiveSessionsPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { PricingPage } from './pages/admin/PricingPage';
import { BillPrintPage } from './pages/BillPrintPage';
import { BillsPage } from './pages/BillsPage';
import { FoodPage } from './pages/FoodPage';
import { InventoryPage } from './pages/InventoryPage';
import { SettingsPage } from './pages/admin/SettingsPage';
import { CustomersPage } from './pages/CustomersPage';
import { DailyClosingPage } from './pages/DailyClosingPage';
import { RentalsPage } from './pages/RentalsPage';
import { ReportsPage } from './pages/ReportsPage';

// Charts are only needed on the dashboard; keep them out of the bundle the counter loads first.
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route path="/bills/:id/print" element={<BillPrintPage />} />
        <Route element={<AppLayout />}>
          {/* Live Sessions is the operator's landing page. */}
          <Route index element={<Navigate to="/live" replace />} />
          <Route path="/live" element={<LiveSessionsPage />} />
          <Route
            path="/dashboard"
            element={
              <Suspense fallback={<CircularProgress />}>
                <DashboardPage />
              </Suspense>
            }
          />
          <Route path="/bills" element={<BillsPage />} />
          <Route path="/customers" element={<CustomersPage />} />
          <Route path="/food" element={<FoodPage />} />
          <Route path="/rentals" element={<RentalsPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/closing" element={<DailyClosingPage />} />
          <Route path="/inventory" element={<InventoryPage />} />
          <Route element={<RequireAuth roles={['ADMIN']} />}>
            <Route path="/pricing" element={<PricingPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
