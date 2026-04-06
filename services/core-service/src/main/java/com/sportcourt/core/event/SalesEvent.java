package com.sportcourt.core.event;

import com.sportcourt.core.domain.enums.SalesOrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class SalesEvent {
    private String schemaVersion;
    private UUID eventId;
    private SalesEventType type;
    private UUID orderId;
    private UUID bookingId;
    private UUID venueId;
    private UUID customerId;
    private SalesOrderStatus status;
    private BigDecimal totalAmount;
    private OffsetDateTime occurredAt;
}
