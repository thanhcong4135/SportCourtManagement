package com.sportcourt.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_read_model")
@Getter
@Setter
public class BookingReadModel {

    @Id
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "court_id")
    private UUID courtId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "payment_status", length = 32)
    private String paymentStatus;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "price_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceTotal;

    @Column(name = "deposit_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositPaid;

    @Column(name = "last_event_type", length = 64)
    private String lastEventType;

    @Column(name = "last_occurred_at")
    private OffsetDateTime lastOccurredAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public BookingReadModel() {
    }
}
