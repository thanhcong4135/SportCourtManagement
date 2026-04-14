import { apiFetch, createIdempotencyKey } from "../../lib/api";

export type NotificationStatus = "QUEUED" | "SENT" | "FAILED";

export type NotificationMessage = {
  id: string;
  status: NotificationStatus;
  recipient: string;
  channel: string;
  templateCode: string;
  message: string;
  bookingId?: string;
  paymentId?: string;
  customerId?: string;
  sourceEventId?: string;
  sourceEventType?: string;
  traceId?: string;
  retryCount: number;
  lastError?: string;
  createdAt: string;
  sentAt?: string;
  updatedAt: string;
};

export type SpringPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export async function listNotifications(params: {
  bookingId?: string;
  customerId?: string;
  status?: NotificationStatus;
  page?: number;
  size?: number;
}) {
  const search = new URLSearchParams();
  if (params.bookingId) {
    search.set("bookingId", params.bookingId);
  }
  if (params.customerId) {
    search.set("customerId", params.customerId);
  }
  if (params.status) {
    search.set("status", params.status);
  }
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  search.set("sort", "createdAt,desc");
  return apiFetch<SpringPage<NotificationMessage>>(`/api/notifications?${search.toString()}`);
}

export async function retryNotification(notificationId: string) {
  return apiFetch<NotificationMessage>(`/api/notifications/${notificationId}/retry`, {
    method: "POST",
    headers: {
      "Idempotency-Key": createIdempotencyKey("notification-retry"),
    },
  });
}
