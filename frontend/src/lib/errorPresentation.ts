import { ApiRequestError } from "./api";

export type ErrorPresentation = {
  message: string;
  traceId?: string;
  fieldErrors: Record<string, string>;
};

function normalizeDetails(details: unknown): Record<string, string> {
  if (!details || typeof details !== "object") {
    return {};
  }

  if (Array.isArray(details)) {
    return {};
  }

  const entries = Object.entries(details as Record<string, unknown>);
  const mapped: Record<string, string> = {};
  for (const [key, value] of entries) {
    if (typeof value === "string" && value.trim()) {
      mapped[key] = value;
      continue;
    }
    if (Array.isArray(value) && value.length > 0) {
      mapped[key] = value.map((item) => String(item)).join(", ");
    }
  }
  return mapped;
}

export function toErrorPresentation(error: unknown, fallback = "Đã có lỗi xảy ra"): ErrorPresentation {
  if (error instanceof ApiRequestError) {
    return {
      message: error.message || fallback,
      traceId: error.traceId,
      fieldErrors: normalizeDetails(error.details),
    };
  }

  if (error instanceof Error) {
    return { message: error.message || fallback, fieldErrors: {} };
  }

  return { message: fallback, fieldErrors: {} };
}
