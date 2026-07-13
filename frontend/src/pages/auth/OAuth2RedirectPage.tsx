import { useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import type { AuthTokens } from "../../lib/api";

function parseRoles(rawRoles: string | null): string[] {
  if (!rawRoles) {
    return [];
  }
  return rawRoles
    .split(",")
    .map((role) => role.trim())
    .filter(Boolean);
}

export function OAuth2RedirectPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { completeOAuthLogin } = useAuth();
  const accessToken = searchParams.get("accessToken");
  const refreshToken = searchParams.get("refreshToken");
  const error = !accessToken || !refreshToken ? "Thiếu token đăng nhập Google." : null;

  useEffect(() => {
    if (!accessToken || !refreshToken) {
      return;
    }

    const tokens: AuthTokens = {
      accessToken,
      refreshToken,
      accessTokenExpiresAt: searchParams.get("accessTokenExpiresAt") ?? undefined,
      refreshTokenExpiresAt: searchParams.get("refreshTokenExpiresAt") ?? undefined,
      userId: searchParams.get("userId") ?? undefined,
      email: searchParams.get("email") ?? undefined,
      roles: parseRoles(searchParams.get("roles")),
    };

    completeOAuthLogin(tokens);
    navigate("/discover", { replace: true });
  }, [accessToken, completeOAuthLogin, navigate, refreshToken, searchParams]);

  return (
    <div className="auth-screen auth-bg">
      <header className="simple-topbar auth-topbar">
        <Link to="/auth/login" className="back-link">←</Link>
        <h1>Đăng nhập Google</h1>
        <div className="topbar-spacer" />
      </header>
      <main className="auth-card-wrap">
        <section className="auth-card">
          {error ? <p className="inline-error">{error}</p> : <p className="inline-muted">Đang hoàn tất đăng nhập Google...</p>}
          {error ? <Link to="/auth/login" className="ui-button ui-button--primary ui-button--md">Quay lại đăng nhập</Link> : null}
        </section>
      </main>
    </div>
  );
}
