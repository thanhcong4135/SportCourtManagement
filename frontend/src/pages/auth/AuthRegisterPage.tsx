import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { Button, InputField } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { type RegisterFormValues, registerSchema } from "../../features/auth/authSchemas";
import { toErrorPresentation } from "../../lib/errorPresentation";

export function AuthRegisterPage() {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: "",
      displayName: "",
      password: "",
      confirmPassword: "",
    },
  });

  async function onSubmit(values: RegisterFormValues) {
    try {
      setError(null);
      setTraceId(null);
      await register({
        email: values.email,
        displayName: values.displayName,
        password: values.password,
      });
      navigate("/discover");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Đăng ký thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
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
        <form className="auth-card" onSubmit={form.handleSubmit(onSubmit)}>
          <InputField
            label="Email của bạn?"
            type="email"
            placeholder="Nhập email"
            autoComplete="email"
            {...form.register("email")}
            error={form.formState.errors.email?.message}
          />

          <InputField
            label="Tên đầy đủ"
            placeholder="Nhập họ và tên"
            autoComplete="name"
            {...form.register("displayName")}
            error={form.formState.errors.displayName?.message}
          />

          <InputField
            label="Mật khẩu"
            type="password"
            placeholder="Nhập mật khẩu"
            autoComplete="new-password"
            {...form.register("password")}
            error={form.formState.errors.password?.message}
          />

          <InputField
            label="Nhập lại mật khẩu"
            type="password"
            placeholder="Nhập lại mật khẩu"
            autoComplete="new-password"
            {...form.register("confirmPassword")}
            error={form.formState.errors.confirmPassword?.message}
          />

          {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}

          <Button type="submit" className="booking-cta" fullWidth disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? "Đang xử lý..." : "ĐĂNG KÝ"}
          </Button>

          <p className="auth-footnote">
            Bạn đã có tài khoản? <Link to="/auth/login">Đăng nhập</Link>
          </p>
        </form>
      </main>
    </div>
  );
}

