import { NavLink } from "react-router-dom";

type BottomNavigationProps = {
  active: "home" | "discover" | "account";
};

export function BottomNavigation({ active }: BottomNavigationProps) {
  return (
    <nav className="alobo-bottom-nav" aria-label="Bottom navigation">
      <NavLink to="/" className={active === "home" ? "active" : ""}>
        <span>Trang chủ</span>
      </NavLink>
      <NavLink to="/discover" className={active === "discover" ? "active" : ""}>
        <span>Khám phá</span>
      </NavLink>
      <NavLink to="/account" className={active === "account" ? "active" : ""}>
        <span>Tài khoản</span>
      </NavLink>
    </nav>
  );
}

