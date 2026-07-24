import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
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
  hint?: string;
  roles: AuthRole[];
  icon: string;
  exact?: boolean;
  tab?: string;
};

const operationsItems: OpsMenuItem[] = [
  { to: "/ops", label: "Tổng quan", roles: OPS_DASHBOARD_ROLES, icon: "●", exact: true, tab: "overview" },
  { to: "/ops?tab=venues", label: "Cụm sân", roles: OPS_DASHBOARD_ROLES, icon: "⌂", tab: "venues" },
  { to: "/ops?tab=courts", label: "Sân", roles: OPS_DASHBOARD_ROLES, icon: "▦", tab: "courts" },
  { to: "/ops?tab=addons", label: "Dịch vụ / Add-on", roles: OPS_DASHBOARD_ROLES, icon: "◇", tab: "addons" },
  { to: "/ops?tab=pricing", label: "Pricing setup", roles: OPS_DASHBOARD_ROLES, icon: "♙", tab: "pricing" },
  { to: "/ops/pricing-rules", label: "Pricing Rules", roles: OPS_PRICING_ROLES, icon: "≡" },
];

const systemItems: OpsMenuItem[] = [
  { to: "/ops/notifications", label: "Notifications", roles: OPS_NOTIFICATIONS_ROLES, icon: "○" },
  { to: "/ops/admin/users", label: "User Management", roles: OPS_USER_MANAGEMENT_ROLES, icon: "○" },
  { to: "/ops/dlq", label: "DLQ Replay", roles: OPS_DLQ_ROLES, icon: "○" },
];

function isDashboardTabActive(item: OpsMenuItem, pathname: string, search: string): boolean {
  if (pathname !== "/ops" || !item.tab) {
    return false;
  }
  const requestedTab = new URLSearchParams(search).get("tab") ?? "overview";
  return requestedTab === item.tab;
}

export function OpsDashboardLayout() {
  const { token, roles, hasAnyRole, logout } = useAuth();
  const location = useLocation();
  const visibleOperationsItems = operationsItems.filter((item) => hasAnyRole(item.roles));
  const visibleSystemItems = systemItems.filter((item) => hasAnyRole(item.roles));

  function renderItem(item: OpsMenuItem) {
    const dashboardActive = isDashboardTabActive(item, location.pathname, location.search);
    if (item.tab) {
      return (
        <Link key={item.to} to={item.to} className={dashboardActive ? "active" : ""}>
          <span className="ops-sidebar-icon" aria-hidden="true">{item.icon}</span>
          <span>{item.label}</span>
        </Link>
      );
    }

    return (
      <NavLink key={item.to} to={item.to} end={item.exact} className={({ isActive }) => (isActive ? "active" : "")}>
        <span className="ops-sidebar-icon" aria-hidden="true">{item.icon}</span>
        <span>{item.label}</span>
        {item.hint ? <small>{item.hint}</small> : null}
      </NavLink>
    );
  }

  return (
    <div className="ops-dashboard-layout">
      <aside className="ops-sidebar">
        <div className="ops-sidebar-brand">
          <strong>SportCourt</strong>
          <span>Admin Console</span>
        </div>

        <nav className="ops-sidebar-menu" aria-label="Ops dashboard menu">
          {visibleOperationsItems.length ? (
            <section className="ops-sidebar-section">
              <p className="ops-sidebar-section-title">Quản lý vận hành</p>
              {visibleOperationsItems.map(renderItem)}
            </section>
          ) : null}

          {visibleSystemItems.length ? (
            <section className="ops-sidebar-section">
              <p className="ops-sidebar-section-title">Hệ thống</p>
              {visibleSystemItems.map(renderItem)}
            </section>
          ) : null}
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
