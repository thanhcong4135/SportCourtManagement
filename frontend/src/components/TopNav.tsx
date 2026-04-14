import { Link, NavLink } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../context/AuthContext";

type AuthMode = "login" | "register";

export function TopNav() {
  const { token, isAuthenticated, login, register, logout } = useAuth();
  const [showAuth, setShowAuth] = useState(false);
  const [mode, setMode] = useState<AuthMode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (mode === "login") {
        await login({ email, password });
      } else {
        await register({ email, password, displayName });
      }
      setShowAuth(false);
      setPassword("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Auth failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <header className="top-nav">
        <Link to="/" className="brand">
          <span className="brand-dot" />
          SportCourt BlueFlow
        </Link>

        <nav className="top-nav-links">
          <NavLink to="/customer" className={({ isActive }) => isActive ? "active" : ""}>Khách hàng</NavLink>
          <NavLink to="/ops" className={({ isActive }) => isActive ? "active" : ""}>Vận hành</NavLink>
        </nav>

        <div className="top-nav-auth">
          {isAuthenticated ? (
            <>
              <span className="user-tag">{token?.email ?? "Đã đăng nhập"}</span>
              <button className="btn ghost" onClick={() => { void logout(); }}>Đăng xuất</button>
            </>
          ) : (
            <button className="btn" onClick={() => { setMode("login"); setShowAuth(true); }}>Đăng nhập</button>
          )}
        </div>
      </header>

      {showAuth && (
        <div className="modal-backdrop" onClick={() => setShowAuth(false)}>
          <div className="auth-modal" onClick={(e) => e.stopPropagation()}>
            <h3>{mode === "login" ? "Đăng nhập" : "Tạo tài khoản"}</h3>
            <p className="muted">Xác thực qua auth-service (JWT) để sử dụng booking flow.</p>

            <form onSubmit={onSubmit} className="stack gap-sm">
              {mode === "register" && (
                <label>
                  Họ tên
                  <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
                </label>
              )}
              <label>
                Email
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
              </label>
              <label>
                Mật khẩu
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required />
              </label>

              {error && <p className="error-text">{error}</p>}

              <div className="inline-actions">
                <button type="submit" className="btn" disabled={loading}>{loading ? "Đang xử lý..." : "Xác nhận"}</button>
                <button
                  type="button"
                  className="btn ghost"
                  onClick={() => setMode(mode === "login" ? "register" : "login")}
                >
                  {mode === "login" ? "Chưa có tài khoản?" : "Đã có tài khoản?"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
