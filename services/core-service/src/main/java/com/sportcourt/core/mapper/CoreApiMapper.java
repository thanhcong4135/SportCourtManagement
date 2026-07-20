package com.sportcourt.core.mapper;

import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.domain.VenueImage;
import com.sportcourt.core.dto.CourtResponse;
import com.sportcourt.core.dto.VenueImageResponse;
import com.sportcourt.core.dto.VenueResponse;

import java.util.List;

public final class CoreApiMapper {

    private CoreApiMapper() {
    }

    public static VenueResponse toVenueResponse(Venue venue) {
        return toVenueResponse(venue, List.of());
    }

    public static VenueResponse toVenueResponse(Venue venue, List<VenueImage> images) {
        String coverImageUrl = venue.getCoverImageUrl();
        return new VenueResponse(
            venue.getId(),
            venue.getName(),
            venue.getAddress(),
            venue.getDescription(),
            coverImageUrl,
            coverImageUrl,
            venue.getPhone(),
            venue.getOpeningTime(),
            venue.getClosingTime(),
            venue.getLatitude(),
            venue.getLongitude(),
            venue.getCreatedAt(),
            images.stream().map(CoreApiMapper::toVenueImageResponse).toList()
        );
    }

    public static VenueImageResponse toVenueImageResponse(VenueImage image) {
        return new VenueImageResponse(
            image.getId(),
            image.getVenue().getId(),
            image.getImageUrl(),
            image.getAltText(),
            image.getSortOrder(),
            image.isCover(),
            image.getCreatedAt()
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
