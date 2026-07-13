import {
  applyPaymentCallback,
  initiateDepositPayment,
  listPaymentByBooking,
  type PaymentTransaction,
} from "../lib/coreApi";

export async function initiateDeposit(params: {
  bookingId: string;
  customerId: string;
  amount: number;
  currency?: string;
  idempotencyKey?: string;
}): Promise<PaymentTransaction> {
  return initiateDepositPayment(params);
}

export async function getPaymentsByBooking(bookingId: string): Promise<PaymentTransaction[]> {
  return listPaymentByBooking(bookingId);
}

export async function submitPaymentCallback(payload: {
  paymentId: string;
  providerReference: string;
  success: boolean;
  failureReason?: string;
}): Promise<PaymentTransaction> {
  return applyPaymentCallback(payload);
}

