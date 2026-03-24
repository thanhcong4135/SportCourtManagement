package com.sportcourt.core.event;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class PaymentEvent {

    private UUID eventId;
    private PaymentEventType type;
    private UUID paymentId;
    private UUID bookingId;
    private UUID customerId;
    private BigDecimal amount;
    private String providerReference;
    private OffsetDateTime occurredAt;
}
