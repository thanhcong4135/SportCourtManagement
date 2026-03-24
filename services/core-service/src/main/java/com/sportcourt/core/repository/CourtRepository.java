package com.sportcourt.core.repository;

import com.sportcourt.core.domain.Court;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourtRepository extends JpaRepository<Court, UUID> {
    List<Court> findByVenueId(UUID venueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Court c where c.id = :id")
    Optional<Court> findByIdForUpdate(@Param("id") UUID id);
}
