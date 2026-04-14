import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function AuthRegisterPage() {
  const navigate = useNavigate();
  const { register } = useAuth();

  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (password !== confirmPassword) {
      setError("Mật khẩu xác nhận không khớp");
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await register({ email, password, displayName });
      navigate("/discover");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Đăng ký thất bại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-screen auth-bg">
      <header className="simple-topbar auth-topbar">
        <Link to="/auth/login" className="back-link">←</Link>
        <h1>Đăng ký</h1>
        <div className="topbar-spacer" />
      </header>

      <main className="auth-card-wrap">
        <form className="auth-card" onSubmit={onSubmit}>
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
            Tên đầy đủ
            <input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Nhập họ và tên"
              required
            />
          </label>

          <label>
            Mật khẩu
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={8}
              placeholder="Nhập mật khẩu"
              required
            />
          </label>

          <label>
            Nhập lại mật khẩu
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              minLength={8}
              placeholder="Nhập lại mật khẩu"
              required
            />
          </label>

          {error && <p className="inline-error">{error}</p>}

          <button type="submit" className="booking-cta" disabled={loading}>
            {loading ? "Đang xử lý..." : "ĐĂNG KÝ"}
          </button>

          <p className="auth-footnote">
            Bạn đã có tài khoản? <Link to="/auth/login">Đăng nhập</Link>
          </p>
        </form>
      </main>
    </div>
  );
}

