import {
  checkAvailability,
  createBookingDraft,
  getBookingById,
  listBookings,
  rescheduleBooking,
  type Booking,
  type BookingPage,
  type CreateDraftPayload,
} from "../lib/coreApi";

export async function getBookings(params: {
  customerId?: string;
  courtId?: string;
  status?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}): Promise<BookingPage> {
  return listBookings(params);
}

export async function getBooking(bookingId: string): Promise<Booking> {
  return getBookingById(bookingId);
}

export async function createDraft(payload: CreateDraftPayload): Promise<Booking> {
  return createBookingDraft(payload);
}

export async function getAvailability(courtId: string, startIso: string, endIso: string): Promise<boolean> {
  const response = await checkAvailability(courtId, startIso, endIso);
  return response.available;
}

export async function updateBookingSlot(
  bookingId: string,
  payload: { courtId?: string; startTime: string; endTime: string; priceTotal?: number },
) {
  return rescheduleBooking(bookingId, payload);
}

