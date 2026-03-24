package com.sportcourt.core.repository;

import com.sportcourt.core.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
