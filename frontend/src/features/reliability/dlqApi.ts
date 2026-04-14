import { apiFetch, createIdempotencyKey } from "../../lib/api";

export type DeadLetterStatus = "RECEIVED" | "REPLAYED" | "FAILED";

export type DeadLetterEvent = {
  id: string;
  sourceTopic: string;
  deadLetterTopic: string;
  kafkaPartition: number;
  kafkaOffset: number;
  eventKey?: string;
  eventId?: string;
  failureReason?: string;
  status: DeadLetterStatus;
  replayCount: number;
  receivedAt: string;
  lastReplayedAt?: string;
};

export type CorePage<T> = {
  items: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
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

export async function listCoreDlq(params: { status?: DeadLetterStatus; page?: number; size?: number }) {
  const search = new URLSearchParams();
  if (params.status) {
    search.set("status", params.status);
  }
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  search.set("sort", "receivedAt,desc");
  return apiFetch<CorePage<DeadLetterEvent>>(`/api/core/ops/dlq?${search.toString()}`);
}

export async function replayCoreDlq(id: string) {
  return apiFetch<DeadLetterEvent>(`/api/core/ops/dlq/${id}/replay`, {
    method: "POST",
    headers: {
      "Idempotency-Key": createIdempotencyKey("core-dlq-replay"),
    },
  });
}

export async function listPaymentDlq(params: { status?: DeadLetterStatus; page?: number; size?: number }) {
  const search = new URLSearchParams();
  if (params.status) {
    search.set("status", params.status);
  }
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  search.set("sort", "receivedAt,desc");
  return apiFetch<SpringPage<DeadLetterEvent>>(`/api/payments/ops/dlq?${search.toString()}`);
}

export async function replayPaymentDlq(id: string) {
  return apiFetch<DeadLetterEvent>(`/api/payments/ops/dlq/${id}/replay`, {
    method: "POST",
    headers: {
      "Idempotency-Key": createIdempotencyKey("payment-dlq-replay"),
    },
  });
}
