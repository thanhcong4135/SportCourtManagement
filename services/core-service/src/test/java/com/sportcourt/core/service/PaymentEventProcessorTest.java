package com.sportcourt.core.service;

import com.sportcourt.core.event.PaymentEvent;
import com.sportcourt.core.event.PaymentEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentEventProcessorTest {

    @Test
    void process_whenDuplicateEvent_shouldSkipBookingUpdate() {
        IdempotentEventService idempotentEventService = mock(IdempotentEventService.class);
        BookingService bookingService = mock(BookingService.class);
        PaymentEventProcessor processor = new PaymentEventProcessor(idempotentEventService, bookingService);
        PaymentEvent event = sampleEvent();

        when(idempotentEventService.tryMarkProcessed("evt-1", "payment.events")).thenReturn(false);

        processor.process(event, "payment.events", "evt-1");

        verify(bookingService, never()).applyPaymentEvent(event);
    }

    @Test
    void process_whenFirstDelivery_shouldApplyBookingUpdate() {
        IdempotentEventService idempotentEventService = mock(IdempotentEventService.class);
        BookingService bookingService = mock(BookingService.class);
        PaymentEventProcessor processor = new PaymentEventProcessor(idempotentEventService, bookingService);
        PaymentEvent event = sampleEvent();

        when(idempotentEventService.tryMarkProcessed("evt-1", "payment.events")).thenReturn(true);

        processor.process(event, "payment.events", "evt-1");

        verify(bookingService).applyPaymentEvent(event);
    }

    private PaymentEvent sampleEvent() {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID());
        event.setType(PaymentEventType.DEPOSIT_SUCCEEDED);
        event.setBookingId(UUID.randomUUID());
        event.setCustomerId(UUID.randomUUID());
        event.setAmount(new BigDecimal("200000"));
        return event;
    }
}
