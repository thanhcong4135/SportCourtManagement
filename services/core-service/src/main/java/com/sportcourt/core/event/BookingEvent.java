package com.sportcourt.core.event;

import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class BookingEvent {
    private String schemaVersion;
    private UUID eventId;
    private BookingEventType type;
    private UUID bookingId;
    private UUID courtId;
    private UUID venueId;
    private UUID customerId;
    private BookingStatus status;
    private PaymentStatus paymentStatus;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private BigDecimal priceTotal;
    private OffsetDateTime occurredAt;
}
