import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { AppLayout } from './components/AppLayout';
import { PlaceholderPage } from './components/PlaceholderPage';
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
            element={<PlaceholderPage title="Dashboard" phase={4} description="Revenue tiles, charts and station utilization." />}
          />
          <Route path="/bills" element={<BillsPage />} />
          <Route
            path="/customers"
            element={<PlaceholderPage title="Customers" phase={4} description="Customer master, search and history." />}
          />
          <Route path="/food" element={<FoodPage />} />
          <Route
            path="/rentals"
            element={<PlaceholderPage title="Game Rentals" phase={4} description="Rent out and return games, late fees and ROI." />}
          />
          <Route
            path="/reports"
            element={<PlaceholderPage title="Reports" phase={4} description="Daily/monthly sales, payment and utilization reports with CSV export." />}
          />
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
