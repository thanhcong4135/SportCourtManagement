import { NavLink, Outlet } from "react-router-dom";
import {
  OPS_DASHBOARD_ROLES,
  OPS_DLQ_ROLES,
  OPS_NOTIFICATIONS_ROLES,
  OPS_PRICING_ROLES,
  OPS_USER_MANAGEMENT_ROLES,
} from "../app/routeRolePolicy";
import { useAuth, type AuthRole } from "../context/AuthContext";

type OpsMenuItem = {
  to: string;
  label: string;
  hint: string;
  roles: AuthRole[];
};

const menuItems: OpsMenuItem[] = [
  { to: "/ops", label: "Dashboard", hint: "KPI tổng quan", roles: OPS_DASHBOARD_ROLES },
  { to: "/ops/notifications", label: "Notifications", hint: "Gửi & retry", roles: OPS_NOTIFICATIONS_ROLES },
  { to: "/ops/pricing-rules", label: "Pricing Rules", hint: "Bảng giá theo sân", roles: OPS_PRICING_ROLES },
  { to: "/ops/admin/users", label: "User Management", hint: "Admin only", roles: OPS_USER_MANAGEMENT_ROLES },
  { to: "/ops/dlq", label: "DLQ Replay", hint: "Reliability tools", roles: OPS_DLQ_ROLES },
];

export function OpsDashboardLayout() {
  const { token, roles, hasAnyRole, logout } = useAuth();
  const visibleItems = menuItems.filter((item) => hasAnyRole(item.roles));

  return (
    <div className="ops-dashboard-layout">
      <aside className="ops-sidebar">
        <div className="ops-sidebar-brand">
          <strong>SportCourt</strong>
          <span>Ops Console</span>
        </div>

        <nav className="ops-sidebar-menu" aria-label="Ops dashboard menu">
          {visibleItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === "/ops"} className={({ isActive }) => (isActive ? "active" : "")}>
              <strong>{item.label}</strong>
              <small>{item.hint}</small>
            </NavLink>
          ))}
        </nav>

        <div className="ops-sidebar-footer">
          <p>{token?.email ?? "Unknown user"}</p>
          <p>{roles.join(", ") || "No role"}</p>
        </div>
      </aside>

      <section className="ops-content-area">
        <header className="ops-topbar">
          <div>
            <p className="eyebrow">Materio-style Admin Layout</p>
            <h2>Dashboard vận hành hệ thống</h2>
          </div>
          <button className="btn ghost" type="button" onClick={() => { void logout(); }}>
            Đăng xuất
          </button>
        </header>

        <div className="ops-page-slot">
          <Outlet />
        </div>
      </section>
    </div>
  );
}
