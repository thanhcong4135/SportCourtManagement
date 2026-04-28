import { ApiRequestError } from "./api";
import { getUserFriendlyErrorMessage } from "./errorMessageCatalog";

export type ErrorPresentation = {
  message: string;
  traceId?: string;
  fieldErrors: Record<string, string>;
};

function normalizeDetails(details: unknown): Record<string, string> {
  if (!details) {
    return {};
  }

  if (Array.isArray(details)) {
    const mapped: Record<string, string> = {};
    details.forEach((item, index) => {
      if (!item || typeof item !== "object") {
        return;
      }
      const row = item as Record<string, unknown>;
      const field = typeof row.field === "string" && row.field.trim() ? row.field.trim() : `item_${index + 1}`;
      const message = typeof row.message === "string" && row.message.trim() ? row.message.trim() : "";
      if (message) {
        mapped[field] = message;
      }
    });
    return mapped;
  }

  if (typeof details !== "object") {
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
      message: getUserFriendlyErrorMessage(error, fallback),
      traceId: error.traceId,
      fieldErrors: normalizeDetails(error.details),
    };
  }

  if (error instanceof Error) {
    return { message: error.message || fallback, fieldErrors: {} };
  }

  return { message: fallback, fieldErrors: {} };
}
