package com.sportcourt.core.domain;

import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.CustomerTier;
import com.sportcourt.core.domain.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking")
@Getter
@Setter
public class Booking {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "price_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceTotal;

    @Column(name = "deposit_required", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositRequired;

    @Column(name = "deposit_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositPaid;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_tier", nullable = false)
    private CustomerTier customerTier;

    @Column(name = "price_snapshot_json", columnDefinition = "TEXT")
    private String priceSnapshotJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Booking() {
    }
}
