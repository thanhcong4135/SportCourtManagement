export type VenueSummary = {
  id: string;
  name: string;
  address: string;
  sports: string[];
  minPrice: number;
  openingHours: string;
  availableSlotsCount: number;
};

export type CourtSummary = {
  id: string;
  venueId: string;
  name: string;
  sportType: string;
};

export type TimeslotStatus = "AVAILABLE" | "HELD" | "BOOKED" | "MAINTENANCE";

export type Timeslot = {
  courtId: string;
  startTime: string;
  endTime: string;
  status: TimeslotStatus;
  price: number;
};

export type BookingDraft = {
  id: string;
  venueId: string;
  courtId: string;
  startTime: string;
  endTime: string;
  subtotal: number;
  depositRequired: number;
  draftExpiryAt: string;
  status: "DRAFT";
};

export type PaymentTransactionView = {
  id: string;
  bookingId: string;
  amount: number;
  currency: string;
  status: "PENDING" | "SUCCESS" | "FAILED" | "CANCELED";
  checkoutUrl?: string;
};

export type UserProfileView = {
  id: string;
  email: string;
  displayName: string;
  phone?: string;
};

export type BookingHistoryItem = {
  id: string;
  venueName: string;
  courtName: string;
  startTime: string;
  endTime: string;
  bookingStatus: string;
  paymentStatus: string;
  totalPrice: number;
};

