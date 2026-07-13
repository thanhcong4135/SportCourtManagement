import { listCourts, listVenues, quoteBooking, type Court, type PricingQuote, type Venue } from "../lib/coreApi";

export async function getVenues(): Promise<Venue[]> {
  return listVenues();
}

export async function getCourtsByVenue(venueId: string): Promise<Court[]> {
  return listCourts(venueId);
}

export async function getCourtQuote(courtId: string, startIso: string, endIso: string): Promise<PricingQuote> {
  return quoteBooking(courtId, startIso, endIso);
}

