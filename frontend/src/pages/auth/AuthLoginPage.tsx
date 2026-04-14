import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function AuthLoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [tab, setTab] = useState<"PHONE" | "EMAIL">("PHONE");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const redirect = searchParams.get("redirect") || "/discover";

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      setLoading(true);
      setError(null);
      await login({ email, password });
      navigate(redirect);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Đăng nhập thất bại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-screen auth-bg">
      <header className="simple-topbar auth-topbar">
        <Link to="/discover" className="back-link">←</Link>
        <h1>Đăng nhập</h1>
        <div className="topbar-spacer" />
      </header>

      <main className="auth-card-wrap">
        <form className="auth-card" onSubmit={onSubmit}>
          <div className="auth-tabs">
            <button type="button" className={tab === "PHONE" ? "active" : ""} onClick={() => setTab("PHONE")}>
              Số điện thoại
            </button>
            <button type="button" className={tab === "EMAIL" ? "active" : ""} onClick={() => setTab("EMAIL")}>
              Email
            </button>
          </div>

          {tab === "PHONE" && (
            <p className="inline-muted">Backend hiện tại login bằng email. Vui lòng nhập email bên dưới.</p>
          )}

          <label>
            Email của bạn?
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="Nhập email"
              required
            />
          </label>

          <label>
            Mật khẩu
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Nhập mật khẩu"
              required
            />
          </label>

          {error && <p className="inline-error">{error}</p>}

          <button type="submit" className="booking-cta" disabled={loading}>
            {loading ? "Đang xử lý..." : "ĐĂNG NHẬP"}
          </button>

          <p className="auth-footnote">
            Bạn chưa có tài khoản? <Link to="/auth/register">Đăng ký</Link>
          </p>
        </form>
      </main>
    </div>
  );
}

