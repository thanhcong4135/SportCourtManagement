import { Navigate, Route, Routes } from "react-router-dom";
import { DiscoverPage } from "./pages/customer/DiscoverPage";
import { BookingGridPage } from "./pages/customer/BookingGridPage";
import { BookingCheckoutPage } from "./pages/customer/BookingCheckoutPage";
import { PaymentPage } from "./pages/customer/PaymentPage";
import { AccountPage } from "./pages/customer/AccountPage";
import { BookingDetailPage } from "./pages/customer/BookingDetailPage";
import { AuthLoginPage } from "./pages/auth/AuthLoginPage";
import { AuthRegisterPage } from "./pages/auth/AuthRegisterPage";
import { OpsPortalPage } from "./pages/ops/OpsPortalPage";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { BatchBookingPage } from "./features/booking/BatchBookingPage";
import { NotificationsPage } from "./features/notification/NotificationsPage";
import { AdminUserManagementPage } from "./features/admin/AdminUserManagementPage";
import { DlqOpsPage } from "./features/reliability/DlqOpsPage";

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/discover" replace />} />
      <Route path="/discover" element={<DiscoverPage />} />
      <Route path="/customer" element={<Navigate to="/discover" replace />} />
      <Route
        path="/booking/grid"
        element={(
          <ProtectedRoute roles={["CUSTOMER", "OWNER", "ADMIN"]}>
            <BookingGridPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/booking/form"
        element={(
          <ProtectedRoute roles={["CUSTOMER", "OWNER", "ADMIN"]}>
            <BookingCheckoutPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/booking/batch"
        element={(
          <ProtectedRoute roles={["CUSTOMER", "OWNER", "ADMIN"]}>
            <BatchBookingPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/payment/:bookingId"
        element={(
          <ProtectedRoute roles={["CUSTOMER", "OWNER", "ADMIN"]}>
            <PaymentPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/account"
        element={(
          <ProtectedRoute roles={["CUSTOMER", "OWNER", "ADMIN"]}>
            <AccountPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/account/bookings/:bookingId"
        element={(
          <ProtectedRoute roles={["CUSTOMER", "OWNER", "ADMIN"]}>
            <BookingDetailPage />
          </ProtectedRoute>
        )}
      />
      <Route path="/auth/login" element={<AuthLoginPage />} />
      <Route path="/auth/register" element={<AuthRegisterPage />} />
      <Route
        path="/ops"
        element={(
          <ProtectedRoute roles={["OWNER", "ADMIN", "STAFF", "SUPPORT"]}>
            <OpsPortalPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/ops/notifications"
        element={(
          <ProtectedRoute roles={["OWNER", "ADMIN", "STAFF", "SUPPORT"]}>
            <NotificationsPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/ops/admin/users"
        element={(
          <ProtectedRoute roles={["ADMIN"]}>
            <AdminUserManagementPage />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/ops/dlq"
        element={(
          <ProtectedRoute roles={["ADMIN"]}>
            <DlqOpsPage />
          </ProtectedRoute>
        )}
      />
      <Route path="*" element={<Navigate to="/discover" replace />} />
    </Routes>
  );
}

export default App;
