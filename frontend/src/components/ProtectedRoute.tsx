import type { ReactElement } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth, type AuthRole } from "../context/AuthContext";

type ProtectedRouteProps = {
  children: ReactElement;
  roles?: AuthRole[];
};

export function ProtectedRoute({ children, roles = [] }: ProtectedRouteProps) {
  const location = useLocation();
  const { isAuthenticated, hasAnyRole } = useAuth();

  if (!isAuthenticated) {
    const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/auth/login?redirect=${redirect}`} replace />;
  }

  if (!hasAnyRole(roles)) {
    return <Navigate to="/discover" replace />;
  }

  return children;
}
