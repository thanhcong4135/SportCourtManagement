import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, InputField } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { type LoginFormValues, loginSchema } from "../../features/auth/authSchemas";
import { getApiBaseUrl } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";

export function AuthLoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<"PHONE" | "EMAIL">("PHONE");
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  });

  const redirect = searchParams.get("redirect") || "/discover";

  async function onSubmit(values: LoginFormValues) {
    try {
      setError(null);
      setTraceId(null);
      await login(values);
      navigate(redirect);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Đăng nhập thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    }
  }

  function loginWithGoogle() {
    window.location.href = `${getApiBaseUrl()}/api/auth/oauth2/google`;
  }

  return (
    <div className="auth-screen auth-bg">
      <header className="simple-topbar auth-topbar">
        <Link to="/discover" className="back-link">←</Link>
        <h1>Đăng nhập</h1>
        <div className="topbar-spacer" />
      </header>

      <main className="auth-card-wrap">
        <form className="auth-card" onSubmit={form.handleSubmit(onSubmit)}>
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

          <InputField
            label="Email của bạn?"
            type="email"
            placeholder="Nhập email"
            autoComplete="email"
            {...form.register("email")}
            error={form.formState.errors.email?.message}
          />

          <InputField
            label="Mật khẩu"
            type="password"
            placeholder="Nhập mật khẩu"
            autoComplete="current-password"
            {...form.register("password")}
            error={form.formState.errors.password?.message}
          />

          {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}

          <Button type="submit" className="booking-cta" fullWidth disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? "Đang xử lý..." : "ĐĂNG NHẬP"}
          </Button>

          <Button type="button" variant="secondary" fullWidth onClick={loginWithGoogle}>
            Đăng nhập với Google
          </Button>

          <p className="auth-footnote">
            Bạn chưa có tài khoản? <Link to="/auth/register">Đăng ký</Link>
          </p>
        </form>
      </main>
    </div>
  );
}
