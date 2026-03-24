package com.sportcourt.core.mapper;

import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.dto.CourtResponse;
import com.sportcourt.core.dto.VenueResponse;

public final class CoreApiMapper {

    private CoreApiMapper() {
    }

    public static VenueResponse toVenueResponse(Venue venue) {
        return new VenueResponse(
            venue.getId(),
            venue.getName(),
            venue.getAddress(),
            venue.getCreatedAt()
        );
    }

    public static CourtResponse toCourtResponse(Court court) {
        return new CourtResponse(
            court.getId(),
            court.getVenue() != null ? court.getVenue().getId() : null,
            court.getName(),
            court.getSportType(),
            court.isActive()
        );
    }
}
