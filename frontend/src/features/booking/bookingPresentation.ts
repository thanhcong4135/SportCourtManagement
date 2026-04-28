import { type Booking, type BookingPaymentStatus, type BookingStatus } from "../../lib/coreApi";

type StatusBadgeVariant = "success" | "warning" | "danger" | "neutral";

export const bookingStatusLabelMap: Record<BookingStatus, string> = {
  DRAFT: "Chờ đặt cọc",
  CONFIRMED: "Đã xác nhận",
  IN_PROGRESS: "Đang chơi",
  COMPLETED: "Hoàn thành",
  CANCELED: "Đã hủy",
  FAILED_TIMEOUT: "Hết hạn đặt cọc",
};

export const paymentStatusLabelMap: Record<BookingPaymentStatus, string> = {
  UNPAID: "Chưa thanh toán",
  DEPOSITED: "Đã đặt cọc",
  PAID: "Đã thanh toán",
  REFUNDED: "Đã hoàn tiền",
  FAILED: "Thanh toán lỗi",
};

export function getBookingStatusLabel(status: BookingStatus) {
  return bookingStatusLabelMap[status] ?? status;
}

export function getPaymentStatusLabel(status: BookingPaymentStatus) {
  return paymentStatusLabelMap[status] ?? status;
}

export function getBookingStatusVariant(status: BookingStatus): StatusBadgeVariant {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "CANCELED":
    case "FAILED_TIMEOUT":
      return "danger";
    case "IN_PROGRESS":
      return "warning";
    case "CONFIRMED":
      return "success";
    default:
      return "neutral";
  }
}

export function getPaymentStatusVariant(status: BookingPaymentStatus): StatusBadgeVariant {
  switch (status) {
    case "PAID":
    case "DEPOSITED":
      return "success";
    case "FAILED":
      return "danger";
    case "UNPAID":
      return "warning";
    default:
      return "neutral";
  }
}

export function canDepositBooking(booking: Booking) {
  return booking.status === "DRAFT"
    && booking.paymentStatus !== "DEPOSITED"
    && booking.paymentStatus !== "PAID";
}

export function canCancelBooking(booking: Booking) {
  return booking.status === "DRAFT" || booking.status === "CONFIRMED";
}

export function canRescheduleBooking(booking: Booking) {
  return booking.status === "DRAFT" || booking.status === "CONFIRMED";
}

export function isWaitingPaymentBooking(booking: Booking) {
  return booking.status === "DRAFT" && (booking.paymentStatus === "UNPAID" || booking.paymentStatus === "FAILED");
}

export function getDetailActionLabel(booking: Booking) {
  if (canDepositBooking(booking)) {
    return "Đặt cọc";
  }
  if (booking.status === "COMPLETED" || booking.status === "CANCELED" || booking.status === "FAILED_TIMEOUT") {
    return "Xem lại";
  }
  return "Chi tiết";
}
