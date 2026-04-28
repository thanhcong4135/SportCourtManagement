/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

type ToastVariant = "info" | "success" | "warning" | "error";

type ToastItem = {
  id: string;
  title: string;
  message?: string;
  variant: ToastVariant;
};

type ToastInput = {
  title: string;
  message?: string;
  variant?: ToastVariant;
  durationMs?: number;
};

type ToastContextValue = {
  showToast: (input: ToastInput) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

function getVariantClass(variant: ToastVariant) {
  switch (variant) {
    case "success":
      return "ui-toast--success";
    case "warning":
      return "ui-toast--warning";
    case "error":
      return "ui-toast--error";
    default:
      return "ui-toast--info";
  }
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((item) => item.id !== id));
  }, []);

  const showToast = useCallback((input: ToastInput) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const next: ToastItem = {
      id,
      title: input.title,
      message: input.message,
      variant: input.variant ?? "info",
    };
    setToasts((current) => [...current.slice(-3), next]);
    const duration = input.durationMs ?? 3200;
    window.setTimeout(() => dismiss(id), duration);
  }, [dismiss]);

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="ui-toast-stack" aria-live="polite" aria-atomic="true">
        {toasts.map((toast) => (
          <div key={toast.id} className={`ui-toast ${getVariantClass(toast.variant)}`}>
            <strong>{toast.title}</strong>
            {toast.message ? <p>{toast.message}</p> : null}
            <button type="button" onClick={() => dismiss(toast.id)} aria-label="Đóng thông báo">
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used inside ToastProvider");
  }
  return context;
}
