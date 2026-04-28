import { useQuery } from "@tanstack/react-query";
import { listCourts, listVenues, type Court, type Venue } from "../../lib/coreApi";
import { venueQueryKeys } from "./queryKeys";

export type DiscoverData = {
  venues: Venue[];
  courtsByVenue: Record<string, Court[]>;
};

async function fetchDiscoverData(): Promise<DiscoverData> {
  const venues = await listVenues();
  const courtPairs = await Promise.all(
    venues.map(async (venue) => {
      const courts = await listCourts(venue.id);
      return [venue.id, courts] as const;
    }),
  );

  return {
    venues,
    courtsByVenue: Object.fromEntries(courtPairs),
  };
}

export function useDiscoverData() {
  return useQuery({
    queryKey: venueQueryKeys.discover(),
    queryFn: fetchDiscoverData,
  });
}
