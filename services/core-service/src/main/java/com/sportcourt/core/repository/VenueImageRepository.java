package com.sportcourt.core.repository;

import com.sportcourt.core.domain.VenueImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueImageRepository extends JpaRepository<VenueImage, UUID> {
    List<VenueImage> findByVenueIdOrderBySortOrderAscCreatedAtAsc(UUID venueId);

    Optional<VenueImage> findByIdAndVenueId(UUID id, UUID venueId);
}
