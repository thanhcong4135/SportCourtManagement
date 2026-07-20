import { useEffect, useState } from "react";
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
  hint: string;
  roles: AuthRole[];
};

type DashboardChildItem = {
  to: string;
  tab: string;
  label: string;
};

const menuItems: OpsMenuItem[] = [
  { to: "/ops", label: "Dashboard", hint: "Tổng quan", roles: OPS_DASHBOARD_ROLES },
  { to: "/ops/notifications", label: "Notifications", hint: "Gửi & retry", roles: OPS_NOTIFICATIONS_ROLES },
  { to: "/ops/pricing-rules", label: "Pricing Rules", hint: "Bảng giá theo sân", roles: OPS_PRICING_ROLES },
  { to: "/ops/admin/users", label: "User Management", hint: "Admin only", roles: OPS_USER_MANAGEMENT_ROLES },
  { to: "/ops/dlq", label: "DLQ Replay", hint: "Reliability tools", roles: OPS_DLQ_ROLES },
];

const dashboardChildren: DashboardChildItem[] = [
  { to: "/ops", tab: "overview", label: "Tổng quan" },
  { to: "/ops?tab=venues", tab: "venues", label: "Cụm sân" },
  { to: "/ops?tab=courts", tab: "courts", label: "Sân" },
  { to: "/ops?tab=addons", tab: "addons", label: "Dịch vụ / Add-on" },
  { to: "/ops?tab=pricing", tab: "pricing", label: "Pricing setup" },
];

export function OpsDashboardLayout() {
  const { token, roles, hasAnyRole, logout } = useAuth();
  const location = useLocation();
  const [dashboardExpanded, setDashboardExpanded] = useState(location.pathname === "/ops");
  const visibleItems = menuItems.filter((item) => hasAnyRole(item.roles));
  const requestedDashboardTab = new URLSearchParams(location.search).get("tab");
  const activeDashboardTab = dashboardChildren.some((child) => child.tab === requestedDashboardTab)
    ? requestedDashboardTab
    : "overview";
  const isDashboardRoute = location.pathname === "/ops";

  useEffect(() => {
    setDashboardExpanded(isDashboardRoute);
  }, [isDashboardRoute]);

  return (
    <div className="ops-dashboard-layout">
      <aside className="ops-sidebar">
        <div className="ops-sidebar-brand">
          <strong>SportCourt</strong>
          <span>Ops Console</span>
        </div>

        <nav className="ops-sidebar-menu" aria-label="Ops dashboard menu">
          {visibleItems.map((item) => {
            const isDashboardItem = item.to === "/ops";
            return (
              <div key={item.to} className={isDashboardItem ? "ops-sidebar-menu-group" : undefined}>
                {isDashboardItem ? (
                  <div
                    className={[
                      "ops-sidebar-parent-row",
                      isDashboardRoute && activeDashboardTab === "overview" ? "active" : "",
                      isDashboardRoute && activeDashboardTab !== "overview" ? "is-parent" : "",
                    ].filter(Boolean).join(" ")}
                  >
                    <NavLink to={item.to} end className="">
                      <strong>{item.label}</strong>
                      <small>{item.hint}</small>
                    </NavLink>
                    <button
                      type="button"
                      className="ops-sidebar-toggle"
                      aria-label={dashboardExpanded ? "Thu gọn Dashboard" : "Mở rộng Dashboard"}
                      aria-expanded={dashboardExpanded}
                      onClick={() => setDashboardExpanded((value) => !value)}
                    >
                      {dashboardExpanded ? "▲" : "▼"}
                    </button>
                  </div>
                ) : (
                  <NavLink to={item.to} end={item.to === "/ops"} className={({ isActive }) => (isActive ? "active" : "")}>
                    <strong>{item.label}</strong>
                    <small>{item.hint}</small>
                  </NavLink>
                )}
                {isDashboardItem && dashboardExpanded ? (
                  <div className="ops-sidebar-submenu" aria-label="Dashboard sections">
                    {dashboardChildren.filter((child) => child.tab !== "overview").map((child) => (
                      <Link key={child.tab} to={child.to} className={activeDashboardTab === child.tab ? "active" : ""}>
                        {child.label}
                      </Link>
                    ))}
                  </div>
                ) : null}
              </div>
            );
          })}
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
