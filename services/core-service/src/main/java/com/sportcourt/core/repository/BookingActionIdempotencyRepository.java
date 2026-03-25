package com.sportcourt.core.repository;

import com.sportcourt.core.domain.BookingActionIdempotency;
import com.sportcourt.core.domain.enums.BookingActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingActionIdempotencyRepository extends JpaRepository<BookingActionIdempotency, UUID> {

    Optional<BookingActionIdempotency> findByBookingIdAndActionTypeAndIdempotencyKey(
        UUID bookingId,
        BookingActionType actionType,
        String idempotencyKey
    );
}
