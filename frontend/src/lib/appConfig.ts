function normalizeBaseUrl(value: string | undefined): string {
  const fallback = "http://localhost:8080";
  const normalized = (value ?? "").trim();
  if (!normalized) {
    return fallback;
  }
  return normalized.endsWith("/") ? normalized.slice(0, -1) : normalized;
}

function normalizeOptional(value: string | undefined): string {
  const normalized = (value ?? "").trim();
  return normalized;
}

export const appConfig = {
  apiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL as string | undefined),
  paymentCallbackSecret: normalizeOptional(import.meta.env.VITE_PAYMENT_CALLBACK_SECRET as string | undefined)
    || "dev-payment-callback-secret",
};
