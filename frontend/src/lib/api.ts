import { appConfig } from "./appConfig";

const API_BASE_URL = appConfig.apiBaseUrl;

export type ApiError = {
  code?: string;
  message?: string;
  details?: unknown;
  traceId?: string;
  status?: number;
  path?: string;
};

export type ApiEnvelope<T> = {
  success: boolean;
  data: T;
  error: ApiError | null;
};

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt?: string;
  refreshTokenExpiresAt?: string;
  userId?: string;
  email?: string;
  roles?: string[];
};

type ApiAuthBridge = {
  getTokens?: () => AuthTokens | null;
  refreshTokens?: () => Promise<AuthTokens | null>;
  clearTokens?: () => void;
};

type ApiFetchInit = RequestInit & {
  skipAuth?: boolean;
};

const apiAuthBridge: ApiAuthBridge = {};

export class ApiRequestError extends Error {
  code?: string;
  details?: unknown;
  traceId?: string;
  status?: number;
  path?: string;

  constructor(message: string, apiError?: ApiError) {
    super(message);
    this.name = "ApiRequestError";
    this.code = apiError?.code;
    this.details = apiError?.details;
    this.traceId = apiError?.traceId;
    this.status = apiError?.status;
    this.path = apiError?.path;
  }
}

export function configureApiAuthBridge(config: ApiAuthBridge) {
  apiAuthBridge.getTokens = config.getTokens;
  apiAuthBridge.refreshTokens = config.refreshTokens;
  apiAuthBridge.clearTokens = config.clearTokens;
}

function safeParsePayload(text: string): unknown {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function parseApiError(payload: unknown, fallbackStatus?: number): ApiError {
  if (payload && typeof payload === "object" && "success" in payload) {
    const envelope = payload as { error?: ApiError | null };
    if (envelope.error) {
      return envelope.error;
    }
  }

  if (payload && typeof payload === "object") {
    const direct = payload as ApiError;
    if (direct.message || direct.code || direct.traceId || direct.status) {
      return direct;
    }
  }

  return { message: typeof payload === "string" ? payload : undefined, status: fallbackStatus };
}

function resolveRequestMessage(error: ApiError, status: number): string {
  if (error.message) {
    return error.message;
  }
  return `HTTP ${status}`;
}

export function createIdempotencyKey(prefix = "sc"): string {
  const randomPart = Math.random().toString(36).slice(2, 10);
  const timePart = Date.now().toString(36);
  return `${prefix}-${timePart}-${randomPart}`;
}

export async function apiFetch<T>(path: string, init: ApiFetchInit = {}, accessToken?: string): Promise<T> {
  return apiFetchInternal(path, init, accessToken, true);
}

async function apiFetchInternal<T>(
  path: string,
  init: ApiFetchInit,
  accessToken: string | undefined,
  allowRefreshRetry: boolean,
): Promise<T> {
  const { skipAuth = false, ...requestInit } = init;
  const headers = new Headers(requestInit.headers || {});
  if (!headers.has("Content-Type") && requestInit.body) {
    headers.set("Content-Type", "application/json");
  }

  const shouldAttachAutoToken = !skipAuth;
  const autoToken = shouldAttachAutoToken ? apiAuthBridge.getTokens?.()?.accessToken : undefined;
  const effectiveToken = accessToken || autoToken;
  const usedAutoToken = Boolean(autoToken && !accessToken);
  if (effectiveToken) {
    headers.set("Authorization", `Bearer ${effectiveToken}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...requestInit, headers });
  const text = await response.text();
  const payload = safeParsePayload(text);

  if (response.status === 401 && allowRefreshRetry && usedAutoToken && apiAuthBridge.refreshTokens) {
    try {
      const refreshed = await apiAuthBridge.refreshTokens();
      if (refreshed?.accessToken) {
        return apiFetchInternal(path, init, refreshed.accessToken, false);
      }
      apiAuthBridge.clearTokens?.();
    } catch {
      apiAuthBridge.clearTokens?.();
    }
  }

  if (!response.ok) {
    const apiError = parseApiError(payload, response.status);
    throw new ApiRequestError(resolveRequestMessage(apiError, response.status), {
      ...apiError,
      status: apiError.status ?? response.status,
    });
  }

  if (payload && typeof payload === "object" && "success" in payload) {
    const envelope = payload as ApiEnvelope<T>;
    if (!envelope.success) {
      throw new ApiRequestError(envelope.error?.message || "Request failed", envelope.error || undefined);
    }
    return envelope.data;
  }

  return payload as T;
}

export function toIsoWithOffset(localDateTime: string): string {
  const date = new Date(localDateTime);
  if (Number.isNaN(date.getTime())) {
    throw new Error("Invalid date time");
  }

  const pad = (n: number) => String(Math.floor(Math.abs(n))).padStart(2, "0");
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hour = pad(date.getHours());
  const minute = pad(date.getMinutes());
  const second = pad(date.getSeconds());

  const offsetMinutes = -date.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? "+" : "-";
  const offsetHour = pad(offsetMinutes / 60);
  const offsetMinute = pad(offsetMinutes % 60);

  return `${year}-${month}-${day}T${hour}:${minute}:${second}${sign}${offsetHour}:${offsetMinute}`;
}

export function formatCurrency(amount: number | string): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(typeof amount === "string" ? Number(amount) : amount);
}

export function getApiBaseUrl(): string {
  return API_BASE_URL;
}

