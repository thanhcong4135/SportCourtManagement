import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import {
  CUSTOMER_BOOKING_ROLES,
  OPS_DASHBOARD_ROLES,
  OPS_DLQ_ROLES,
  OPS_NOTIFICATIONS_ROLES,
  OPS_PRICING_ROLES,
  OPS_USER_MANAGEMENT_ROLES,
} from "./app/routeRolePolicy";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { OpsDashboardLayout } from "./layouts/OpsDashboardLayout";

const LandingPage = lazy(() => import("./pages/LandingPage").then((module) => ({ default: module.LandingPage })));
const DiscoverPage = lazy(() => import("./pages/customer/DiscoverPage").then((module) => ({ default: module.DiscoverPage })));
const VenueDetailPage = lazy(() => import("./pages/customer/VenueDetailPage").then((module) => ({ default: module.VenueDetailPage })));
const BookingGridPage = lazy(() => import("./pages/customer/BookingGridPage").then((module) => ({ default: module.BookingGridPage })));
const BookingCheckoutPage = lazy(() => import("./pages/customer/BookingCheckoutPage").then((module) => ({ default: module.BookingCheckoutPage })));
const BatchBookingPage = lazy(() => import("./features/booking/BatchBookingPage").then((module) => ({ default: module.BatchBookingPage })));
const PaymentPage = lazy(() => import("./pages/customer/PaymentPage").then((module) => ({ default: module.PaymentPage })));
const AccountPage = lazy(() => import("./pages/customer/AccountPage").then((module) => ({ default: module.AccountPage })));
const BookingDetailPage = lazy(() => import("./pages/customer/BookingDetailPage").then((module) => ({ default: module.BookingDetailPage })));
const AuthLoginPage = lazy(() => import("./pages/auth/AuthLoginPage").then((module) => ({ default: module.AuthLoginPage })));
const AuthRegisterPage = lazy(() => import("./pages/auth/AuthRegisterPage").then((module) => ({ default: module.AuthRegisterPage })));
const OpsPortalPage = lazy(() => import("./pages/ops/OpsPortalPage").then((module) => ({ default: module.OpsPortalPage })));
const NotificationsPage = lazy(() => import("./features/notification/NotificationsPage").then((module) => ({ default: module.NotificationsPage })));
const AdminUserManagementPage = lazy(() => import("./features/admin/AdminUserManagementPage").then((module) => ({ default: module.AdminUserManagementPage })));
const PricingRuleManagementPage = lazy(() => import("./features/admin/PricingRuleManagementPage").then((module) => ({ default: module.PricingRuleManagementPage })));
const DlqOpsPage = lazy(() => import("./features/reliability/DlqOpsPage").then((module) => ({ default: module.DlqOpsPage })));

function App() {
  return (
    <Suspense fallback={<div className="route-fallback">Đang tải trang...</div>}>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/discover" element={<DiscoverPage />} />
        <Route path="/venues/:venueId" element={<VenueDetailPage />} />
        <Route path="/customer" element={<Navigate to="/discover" replace />} />
        <Route
          path="/booking/grid"
          element={(
            <ProtectedRoute roles={CUSTOMER_BOOKING_ROLES}>
              <BookingGridPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/booking/form"
          element={(
            <ProtectedRoute roles={CUSTOMER_BOOKING_ROLES}>
              <BookingCheckoutPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/booking/batch"
          element={(
            <ProtectedRoute roles={CUSTOMER_BOOKING_ROLES}>
              <BatchBookingPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/payment/:bookingId"
          element={(
            <ProtectedRoute roles={CUSTOMER_BOOKING_ROLES}>
              <PaymentPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/account"
          element={(
            <ProtectedRoute roles={CUSTOMER_BOOKING_ROLES}>
              <AccountPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/account/bookings/:bookingId"
          element={(
            <ProtectedRoute roles={CUSTOMER_BOOKING_ROLES}>
              <BookingDetailPage />
            </ProtectedRoute>
          )}
        />
        <Route path="/auth/login" element={<AuthLoginPage />} />
        <Route path="/auth/register" element={<AuthRegisterPage />} />
        <Route
          path="/ops"
          element={(
            <ProtectedRoute roles={OPS_DASHBOARD_ROLES}>
              <OpsDashboardLayout />
            </ProtectedRoute>
          )}
        >
          <Route index element={<OpsPortalPage />} />
          <Route
            path="notifications"
            element={(
              <ProtectedRoute roles={OPS_NOTIFICATIONS_ROLES}>
                <NotificationsPage />
              </ProtectedRoute>
            )}
          />
          <Route
            path="pricing-rules"
            element={(
              <ProtectedRoute roles={OPS_PRICING_ROLES}>
                <PricingRuleManagementPage />
              </ProtectedRoute>
            )}
          />
          <Route
            path="admin/users"
            element={(
              <ProtectedRoute roles={OPS_USER_MANAGEMENT_ROLES}>
                <AdminUserManagementPage />
              </ProtectedRoute>
            )}
          />
          <Route
            path="dlq"
            element={(
              <ProtectedRoute roles={OPS_DLQ_ROLES}>
                <DlqOpsPage />
              </ProtectedRoute>
            )}
          />
        </Route>
        <Route path="*" element={<Navigate to="/discover" replace />} />
      </Routes>
    </Suspense>
  );
}

export default App;
