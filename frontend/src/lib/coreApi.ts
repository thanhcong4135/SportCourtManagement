import { apiFetch, createIdempotencyKey, toIsoWithOffset } from "./api";

export type Venue = {
  id: string;
  name: string;
  address: string;
};

export type Court = {
  id: string;
  venueId: string;
  name: string;
  sportType: string;
};

export type BookingStatus = "DRAFT" | "CONFIRMED" | "IN_PROGRESS" | "COMPLETED" | "CANCELED" | "FAILED_TIMEOUT";
export type BookingPaymentStatus = "UNPAID" | "DEPOSITED" | "PAID" | "REFUNDED" | "FAILED";

export type Booking = {
  id: string;
  courtId: string;
  customerId: string;
  status: BookingStatus;
  paymentStatus: BookingPaymentStatus;
  startTime: string;
  endTime: string;
  priceTotal: number;
  depositRequired: number;
  depositPaid: number;
};

export type BookingPage = {
  items: Booking[];
  totalElements?: number;
  totalPages?: number;
  pageNumber?: number;
  pageSize?: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
};

export type Product = {
  id: string;
  venueId: string;
  name: string;
  unitPrice: number;
  active: boolean;
};

export type PricingQuote = {
  totalPrice: number;
};

export type CreateDraftPayload = {
  courtId: string;
  startTime: string;
  endTime: string;
  priceTotal: number;
};

export type BatchBookingDraftPayload = {
  items: Array<CreateDraftPayload>;
};

export type BatchDepositPayload = {
  items: Array<{
    bookingId: string;
    amount: number;
  }>;
};

export type BatchConfirmPayload = {
  bookingIds: string[];
};

export type BatchBookingActionResponse = {
  bookings: Booking[];
  totalPrice: number;
  totalDepositRequired: number;
  totalDepositPaid: number;
};

export type PaymentTransactionStatus = "PENDING" | "SUCCESS" | "FAILED" | "CANCELED";

export type PaymentTransaction = {
  id: string;
  bookingId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: PaymentTransactionStatus;
  provider: string;
  providerReference?: string;
  checkoutUrl?: string;
  requestedAt: string;
  completedAt?: string;
  failureReason?: string;
  idempotencyKey: string;
};

export async function listVenues() {
  return apiFetch<Venue[]>("/api/core/venues");
}

export async function listCourts(venueId: string) {
  return apiFetch<Court[]>(`/api/core/courts?venueId=${venueId}`);
}

export async function listProducts(venueId: string, activeOnly = true) {
  return apiFetch<Product[]>(`/api/core/products?venueId=${venueId}&activeOnly=${activeOnly}`);
}

export async function listBookings(
  params: { customerId?: string; courtId?: string; status?: string; from?: string; to?: string; page?: number; size?: number },
) {
  const search = new URLSearchParams();
  if (params.customerId) {
    search.set("customerId", params.customerId);
  }
  if (params.courtId) {
    search.set("courtId", params.courtId);
  }
  if (params.status) {
    search.set("status", params.status);
  }
  if (params.from) {
    search.set("from", params.from);
  }
  if (params.to) {
    search.set("to", params.to);
  }
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  return apiFetch<BookingPage>(`/api/core/bookings?${search.toString()}`);
}

export async function getBookingById(bookingId: string) {
  return apiFetch<Booking>(`/api/core/bookings/${bookingId}`);
}

export async function checkAvailability(courtId: string, startIso: string, endIso: string) {
  const start = encodeURIComponent(startIso);
  const end = encodeURIComponent(endIso);
  return apiFetch<{ available: boolean }>(`/api/core/availability?courtId=${courtId}&start=${start}&end=${end}`);
}

export async function quoteBooking(courtId: string, startIso: string, endIso: string) {
  const start = encodeURIComponent(startIso);
  const end = encodeURIComponent(endIso);
  return apiFetch<PricingQuote>(
    `/api/core/pricing/quote?courtId=${courtId}&start=${start}&end=${end}&customerTier=STANDARD`,
  );
}

export async function createBookingDraft(payload: CreateDraftPayload, idempotencyKey?: string) {
  return apiFetch<Booking>(
    "/api/core/bookings/draft",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-draft"),
      },
      body: JSON.stringify(payload),
    },
  );
}

export async function createBookingDraftBatch(payload: BatchBookingDraftPayload, idempotencyKey?: string) {
  return apiFetch<BatchBookingActionResponse>(
    "/api/core/bookings/draft/batch",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-draft-batch"),
      },
      body: JSON.stringify(payload),
    },
  );
}

export async function depositBooking(bookingId: string, amount: number, idempotencyKey?: string) {
  return apiFetch<Booking>(
    `/api/core/bookings/${bookingId}/deposit`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-deposit"),
      },
      body: JSON.stringify({ amount }),
    },
  );
}

export async function depositBookingBatch(payload: BatchDepositPayload, idempotencyKey?: string) {
  return apiFetch<BatchBookingActionResponse>(
    "/api/core/bookings/deposit/batch",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-deposit-batch"),
      },
      body: JSON.stringify(payload),
    },
  );
}

export async function confirmBooking(bookingId: string, idempotencyKey?: string) {
  return apiFetch<Booking>(
    `/api/core/bookings/${bookingId}/confirm`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-confirm"),
      },
    },
  );
}

export async function confirmBookingBatch(payload: BatchConfirmPayload, idempotencyKey?: string) {
  return apiFetch<BatchBookingActionResponse>(
    "/api/core/bookings/confirm/batch",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-confirm-batch"),
      },
      body: JSON.stringify(payload),
    },
  );
}

export async function cancelBooking(bookingId: string, idempotencyKey?: string) {
  return apiFetch<Booking>(
    `/api/core/bookings/${bookingId}/cancel`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-cancel"),
      },
    },
  );
}

export async function rescheduleBooking(
  bookingId: string,
  payload: { courtId?: string; startTime: string; endTime: string; priceTotal?: number },
  idempotencyKey?: string,
) {
  return apiFetch<Booking>(
    `/api/core/bookings/${bookingId}/reschedule`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-reschedule"),
      },
      body: JSON.stringify(payload),
    },
  );
}

export async function createOrder(payload: {
  bookingId: string;
  venueId: string;
  items: Array<{ productId: string; quantity: number }>;
}, idempotencyKey?: string) {
  return apiFetch(
    "/api/core/orders",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("order-create"),
      },
      body: JSON.stringify(payload),
    },
  );
}

export async function initiateDepositPayment(payload: {
  bookingId: string;
  customerId: string;
  amount: number;
  currency?: string;
  idempotencyKey?: string;
}) {
  return apiFetch<PaymentTransaction>(
    "/api/payments/deposits/initiate",
    {
      method: "POST",
      body: JSON.stringify({
        bookingId: payload.bookingId,
        customerId: payload.customerId,
        amount: payload.amount,
        currency: payload.currency || "VND",
        idempotencyKey: payload.idempotencyKey || createIdempotencyKey("payment-deposit"),
      }),
    },
  );
}

export async function listPaymentByBooking(bookingId: string) {
  return apiFetch<PaymentTransaction[]>(`/api/payments/booking/${bookingId}`);
}

export async function getPaymentById(paymentId: string) {
  return apiFetch<PaymentTransaction>(`/api/payments/${paymentId}`);
}

export async function applyPaymentCallback(payload: {
  paymentId: string;
  providerReference: string;
  success: boolean;
  failureReason?: string;
}) {
  return apiFetch<PaymentTransaction>(
    "/api/payments/callback",
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
}

export function buildOffsetIso(date: string, time: string): string {
  return toIsoWithOffset(`${date}T${time}`);
}

export function buildDateRangeIso(date: string) {
  const from = toIsoWithOffset(`${date}T00:00:00`);
  const to = toIsoWithOffset(`${date}T23:59:59`);
  return { from, to };
}

export function toLocalDateTime(iso: string): string {
  const date = new Date(iso);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${d}/${m}/${y} ${hh}:${mm}`;
}
