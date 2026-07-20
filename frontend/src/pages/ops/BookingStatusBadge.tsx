import { StatusBadge } from "../../components/ui/StatusBadge";
import type { BookingPaymentStatus, BookingStatus } from "../../lib/coreApi";

type Props = {
  status?: BookingStatus;
  paymentStatus?: BookingPaymentStatus;
  compact?: boolean;
};

type BadgeVariant = "success" | "warning" | "danger" | "neutral";

const bookingLabels: Record<BookingStatus, string> = {
  DRAFT: "Cho dat coc",
  CONFIRMED: "Da xac nhan",
  IN_PROGRESS: "Dang choi",
  COMPLETED: "Hoan thanh",
  CANCELED: "Da huy",
  FAILED_TIMEOUT: "Het han",
};

const paymentLabels: Record<BookingPaymentStatus, string> = {
  UNPAID: "Chua tra",
  DEPOSITED: "Da coc",
  PAID: "Da tra",
  REFUNDED: "Hoan tien",
  FAILED: "Loi thanh toan",
};

function bookingVariant(status: BookingStatus): BadgeVariant {
  if (status === "COMPLETED" || status === "CONFIRMED") {
    return "success";
  }
  if (status === "IN_PROGRESS" || status === "DRAFT") {
    return "warning";
  }
  if (status === "CANCELED" || status === "FAILED_TIMEOUT") {
    return "danger";
  }
  return "neutral";
}

function paymentVariant(status: BookingPaymentStatus): BadgeVariant {
  if (status === "PAID" || status === "DEPOSITED") {
    return "success";
  }
  if (status === "FAILED") {
    return "danger";
  }
  if (status === "UNPAID") {
    return "warning";
  }
  return "neutral";
}

export function BookingStatusBadge({ status, paymentStatus, compact = false }: Props) {
  return (
    <span className={compact ? "booking-badge-row booking-badge-row--compact" : "booking-badge-row"}>
      {status ? <StatusBadge label={bookingLabels[status] ?? status} variant={bookingVariant(status)} /> : null}
      {paymentStatus ? <StatusBadge label={paymentLabels[paymentStatus] ?? paymentStatus} variant={paymentVariant(paymentStatus)} /> : null}
    </span>
  );
}
