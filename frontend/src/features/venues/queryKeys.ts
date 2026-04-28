export const venueQueryKeys = {
  all: ["venues"] as const,
  discover: () => [...venueQueryKeys.all, "discover"] as const,
};

