package com.sportcourt.core.service;

import com.sportcourt.core.event.PaymentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventProcessor {

    private final IdempotentEventService idempotentEventService;
    private final BookingService bookingService;

    public PaymentEventProcessor(IdempotentEventService idempotentEventService, BookingService bookingService) {
        this.idempotentEventService = idempotentEventService;
        this.bookingService = bookingService;
    }

    @Transactional
    public void process(PaymentEvent paymentEvent, String topic, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalStateException("Missing event id for payment event");
        }

        boolean firstDelivery = idempotentEventService.tryMarkProcessed(eventId, topic);
        if (!firstDelivery) {
            return;
        }

        bookingService.applyPaymentEvent(paymentEvent);
    }
}
