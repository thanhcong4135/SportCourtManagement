function asString(value: unknown, fallback = ""): string {
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return fallback;
}

function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : fallback;
  }
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function asOptionalString(value: unknown): string | null {
  const result = asString(value).trim();
  return result ? result : null;
}

function asOptionalNumber(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  const parsed = asNumber(value, Number.NaN);
  return Number.isFinite(parsed) ? parsed : null;
}

function asBoolean(value: unknown, fallback = false): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    return value !== 0;
  }
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (normalized === "true" || normalized === "1") {
      return true;
    }
    if (normalized === "false" || normalized === "0") {
      return false;
    }
  }
  return fallback;
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function toEnum<T extends string>(value: unknown, allowed: readonly T[], fallback: T): T {
  const normalized = asString(value, fallback).toUpperCase() as T;
  return allowed.includes(normalized) ? normalized : fallback;
}

export function mapVenue(value: unknown) {
  const dto = asObject(value);
  const coverImageUrl = asOptionalString(dto.coverImageUrl) ?? asOptionalString(dto.imageUrl);
  return {
    id: asString(dto.id),
    name: asString(dto.name, "Unnamed venue"),
    address: asString(dto.address, ""),
    description: asOptionalString(dto.description),
    coverImageUrl,
    imageUrl: coverImageUrl,
    phone: asOptionalString(dto.phone),
    openingTime: asOptionalString(dto.openingTime),
    closingTime: asOptionalString(dto.closingTime),
    latitude: asOptionalNumber(dto.latitude),
    longitude: asOptionalNumber(dto.longitude),
    images: asArray(dto.images).map(mapVenueImage),
  };
}

export function mapVenueImage(value: unknown) {
  const dto = asObject(value);
  return {
    id: asString(dto.id),
    venueId: asString(dto.venueId),
    imageUrl: asString(dto.imageUrl),
    altText: asOptionalString(dto.altText),
    sortOrder: asNumber(dto.sortOrder, 0),
    cover: asBoolean(dto.cover, false),
    createdAt: asString(dto.createdAt),
  };
}

export function mapCourt(value: unknown) {
  const dto = asObject(value);
  return {
    id: asString(dto.id),
    venueId: asString(dto.venueId),
    name: asString(dto.name, "Unnamed court"),
    sportType: asString(dto.sportType, "UNKNOWN"),
  };
}

const bookingStatusValues = ["DRAFT", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELED", "FAILED_TIMEOUT"] as const;
const paymentStatusValues = ["UNPAID", "DEPOSITED", "PAID", "REFUNDED", "FAILED"] as const;
const paymentTxStatusValues = ["PENDING", "SUCCESS", "FAILED", "CANCELED"] as const;
const pricingDayValues = ["ALL", "WEEKDAY", "WEEKEND"] as const;
const pricingTierValues = ["ALL", "STANDARD", "MEMBER", "VIP"] as const;

export function mapBooking(value: unknown) {
  const dto = asObject(value);
  return {
    id: asString(dto.id),
    courtId: asString(dto.courtId),
    customerId: asString(dto.customerId),
    status: toEnum(dto.status, bookingStatusValues, "DRAFT"),
    paymentStatus: toEnum(dto.paymentStatus, paymentStatusValues, "UNPAID"),
    startTime: asString(dto.startTime),
    endTime: asString(dto.endTime),
    priceTotal: asNumber(dto.priceTotal),
    depositRequired: asNumber(dto.depositRequired),
    depositPaid: asNumber(dto.depositPaid),
  };
}

export function mapBookingPage(value: unknown) {
  const dto = asObject(value);
  return {
    items: asArray(dto.items).map(mapBooking),
    totalElements: asNumber(dto.totalElements, 0),
    totalPages: asNumber(dto.totalPages, 0),
    pageNumber: asNumber(dto.pageNumber, 0),
    pageSize: asNumber(dto.pageSize, 0),
    hasNext: asBoolean(dto.hasNext, false),
    hasPrevious: asBoolean(dto.hasPrevious, false),
  };
}

export function mapPricingQuote(value: unknown) {
  const dto = asObject(value);
  return {
    totalPrice: asNumber(dto.totalPrice),
  };
}

export function mapPricingRule(value: unknown) {
  const dto = asObject(value);
  return {
    id: asString(dto.id),
    courtId: asString(dto.courtId),
    name: asString(dto.name),
    dayType: toEnum(dto.dayType, pricingDayValues, "ALL"),
    startTime: asString(dto.startTime),
    endTime: asString(dto.endTime),
    customerTier: toEnum(dto.customerTier, pricingTierValues, "ALL"),
    pricePerHour: asNumber(dto.pricePerHour),
    priority: asNumber(dto.priority, 0),
    active: asBoolean(dto.active, true),
    createdAt: asString(dto.createdAt),
    updatedAt: asString(dto.updatedAt),
  };
}

export function mapPaymentTransaction(value: unknown) {
  const dto = asObject(value);
  return {
    id: asString(dto.id),
    paymentRef: asString(dto.paymentRef),
    bookingId: asString(dto.bookingId),
    customerId: asString(dto.customerId),
    amount: asNumber(dto.amount),
    currency: asString(dto.currency, "VND"),
    status: toEnum(dto.status, paymentTxStatusValues, "PENDING"),
    provider: asString(dto.provider),
    providerReference: asString(dto.providerReference),
    providerTransactionNo: asString(dto.providerTransactionNo),
    bankCode: asString(dto.bankCode),
    responseCode: asString(dto.responseCode),
    transactionStatus: asString(dto.transactionStatus),
    payDate: asString(dto.payDate),
    checkoutUrl: asString(dto.checkoutUrl),
    requestedAt: asString(dto.requestedAt),
    updatedAt: asString(dto.updatedAt),
    completedAt: asString(dto.completedAt),
    failureReason: asString(dto.failureReason),
    idempotencyKey: asString(dto.idempotencyKey),
  };
}

export function mapBatchBookingActionResponse(value: unknown) {
  const dto = asObject(value);
  return {
    bookings: asArray(dto.bookings).map(mapBooking),
    totalPrice: asNumber(dto.totalPrice),
    totalDepositRequired: asNumber(dto.totalDepositRequired),
    totalDepositPaid: asNumber(dto.totalDepositPaid),
  };
}
