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
@Table(name = "sales_order_read_model")
@Getter
@Setter
public class SalesOrderReadModel {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "last_event_type", length = 64)
    private String lastEventType;

    @Column(name = "last_occurred_at")
    private OffsetDateTime lastOccurredAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public SalesOrderReadModel() {
    }
}
