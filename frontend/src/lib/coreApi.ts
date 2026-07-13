import { apiFetch, createIdempotencyKey, toIsoWithOffset } from "./api";
import { appConfig } from "./appConfig";
import {
  mapBatchBookingActionResponse,
  mapBooking,
  mapBookingPage,
  mapCourt,
  mapPaymentTransaction,
  mapPricingQuote,
  mapPricingRule,
  mapVenue,
} from "./coreApiMapper";

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

export type PricingDayType = "ALL" | "WEEKDAY" | "WEEKEND";
export type PricingRuleCustomerTier = "ALL" | "STANDARD" | "MEMBER" | "VIP";

export type PricingRule = {
  id: string;
  courtId: string;
  name: string;
  dayType: PricingDayType;
  startTime: string;
  endTime: string;
  customerTier: PricingRuleCustomerTier;
  pricePerHour: number;
  priority: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreatePricingRulePayload = {
  courtId: string;
  name: string;
  dayType: PricingDayType;
  startTime: string;
  endTime: string;
  customerTier: PricingRuleCustomerTier;
  pricePerHour: number;
  priority?: number;
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
  paymentRef?: string;
  bookingId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: PaymentTransactionStatus;
  provider: string;
  providerReference?: string;
  providerTransactionNo?: string;
  bankCode?: string;
  responseCode?: string;
  transactionStatus?: string;
  payDate?: string;
  checkoutUrl?: string;
  requestedAt: string;
  updatedAt?: string;
  completedAt?: string;
  failureReason?: string;
  idempotencyKey: string;
};

export type VnpayCreatePaymentResponse = {
  paymentId: string;
  paymentRef: string;
  paymentUrl: string;
};

export type PaymentByRefStatus = {
  paymentRef: string;
  bookingId: string;
  amount: number;
  provider: string;
  status: PaymentTransactionStatus;
  responseCode?: string;
  transactionStatus?: string;
};

export async function listVenues() {
  const response = await apiFetch<unknown[]>("/api/core/venues");
  return response.map((item) => mapVenue(item) as Venue);
}

export async function listCourts(venueId: string) {
  const response = await apiFetch<unknown[]>(`/api/core/courts?venueId=${venueId}`);
  return response.map((item) => mapCourt(item) as Court);
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
  const response = await apiFetch<unknown>(`/api/core/bookings?${search.toString()}`);
  return mapBookingPage(response) as BookingPage;
}

export async function getBookingById(bookingId: string) {
  const response = await apiFetch<unknown>(`/api/core/bookings/${bookingId}`);
  return mapBooking(response) as Booking;
}

export async function checkAvailability(courtId: string, startIso: string, endIso: string) {
  const start = encodeURIComponent(startIso);
  const end = encodeURIComponent(endIso);
  return apiFetch<{ available: boolean }>(`/api/core/availability?courtId=${courtId}&start=${start}&end=${end}`);
}

export async function quoteBooking(courtId: string, startIso: string, endIso: string) {
  const start = encodeURIComponent(startIso);
  const end = encodeURIComponent(endIso);
  const response = await apiFetch<unknown>(
    `/api/core/pricing/quote?courtId=${courtId}&start=${start}&end=${end}&customerTier=STANDARD`,
  );
  return mapPricingQuote(response) as PricingQuote;
}

export async function listPricingRules(courtId?: string) {
  const query = courtId ? `?courtId=${courtId}` : "";
  const response = await apiFetch<unknown[]>(`/api/core/pricing-rules${query}`);
  return response.map((item) => mapPricingRule(item) as PricingRule);
}

export async function createPricingRule(payload: CreatePricingRulePayload, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    "/api/core/pricing-rules",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("pricing-rule-create"),
      },
      body: JSON.stringify(payload),
    },
  );
  return mapPricingRule(response) as PricingRule;
}

export async function createBookingDraft(payload: CreateDraftPayload, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    "/api/core/bookings/draft",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-draft"),
      },
      body: JSON.stringify(payload),
    },
  );
  return mapBooking(response) as Booking;
}

export async function createBookingDraftBatch(payload: BatchBookingDraftPayload, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    "/api/core/bookings/draft/batch",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-draft-batch"),
      },
      body: JSON.stringify(payload),
    },
  );
  return mapBatchBookingActionResponse(response) as BatchBookingActionResponse;
}

export async function depositBooking(bookingId: string, amount: number, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    `/api/core/bookings/${bookingId}/deposit`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-deposit"),
      },
      body: JSON.stringify({ amount }),
    },
  );
  return mapBooking(response) as Booking;
}

export async function depositBookingBatch(payload: BatchDepositPayload, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    "/api/core/bookings/deposit/batch",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-deposit-batch"),
      },
      body: JSON.stringify(payload),
    },
  );
  return mapBatchBookingActionResponse(response) as BatchBookingActionResponse;
}

export async function confirmBooking(bookingId: string, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    `/api/core/bookings/${bookingId}/confirm`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-confirm"),
      },
    },
  );
  return mapBooking(response) as Booking;
}

export async function confirmBookingBatch(payload: BatchConfirmPayload, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    "/api/core/bookings/confirm/batch",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-confirm-batch"),
      },
      body: JSON.stringify(payload),
    },
  );
  return mapBatchBookingActionResponse(response) as BatchBookingActionResponse;
}

export async function cancelBooking(bookingId: string, idempotencyKey?: string) {
  const response = await apiFetch<unknown>(
    `/api/core/bookings/${bookingId}/cancel`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-cancel"),
      },
    },
  );
  return mapBooking(response) as Booking;
}

export async function rescheduleBooking(
  bookingId: string,
  payload: { courtId?: string; startTime: string; endTime: string; priceTotal?: number },
  idempotencyKey?: string,
) {
  const response = await apiFetch<unknown>(
    `/api/core/bookings/${bookingId}/reschedule`,
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey || createIdempotencyKey("booking-reschedule"),
      },
      body: JSON.stringify(payload),
    },
  );
  return mapBooking(response) as Booking;
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
  const response = await apiFetch<unknown>(
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
  return mapPaymentTransaction(response) as PaymentTransaction;
}

export async function createVnpayPayment(payload: {
  bookingId: string;
  customerId?: string;
  amount: number;
  customerName?: string;
  customerPhone?: string;
  orderInfo?: string;
  bankCode?: string;
  idempotencyKey?: string;
}) {
  return apiFetch<VnpayCreatePaymentResponse>(
    "/api/payments/vnpay/create-payment",
    {
      method: "POST",
      body: JSON.stringify({
        bookingId: payload.bookingId,
        customerId: payload.customerId,
        amount: payload.amount,
        customerName: payload.customerName,
        customerPhone: payload.customerPhone,
        orderInfo: payload.orderInfo,
        bankCode: payload.bankCode,
        idempotencyKey: payload.idempotencyKey || createIdempotencyKey("payment-vnpay"),
      }),
    },
  );
}

export async function getPaymentByRef(paymentRef: string) {
  return apiFetch<PaymentByRefStatus>(`/api/payments/by-ref/${encodeURIComponent(paymentRef)}`);
}

export async function listPaymentByBooking(bookingId: string) {
  const response = await apiFetch<unknown[]>(`/api/payments/booking/${bookingId}`);
  return response.map((item) => mapPaymentTransaction(item) as PaymentTransaction);
}

export async function getPaymentById(paymentId: string) {
  const response = await apiFetch<unknown>(`/api/payments/${paymentId}`);
  return mapPaymentTransaction(response) as PaymentTransaction;
}

export async function applyPaymentCallback(payload: {
  paymentId: string;
  providerReference: string;
  success: boolean;
  failureReason?: string;
}) {
  const headers: Record<string, string> = {};
  if (appConfig.paymentCallbackSecret) {
    headers["X-Payment-Callback-Secret"] = appConfig.paymentCallbackSecret;
  }

  const response = await apiFetch<unknown>(
    "/api/payments/callback",
    {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
    },
  );
  return mapPaymentTransaction(response) as PaymentTransaction;
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

