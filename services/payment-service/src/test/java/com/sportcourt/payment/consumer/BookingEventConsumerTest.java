package com.sportcourt.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.payment.service.PaymentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BookingEventConsumerTest {

    @Test
    void consumeBookingEvent_shouldProcessWhenSchemaVersionSupported() {
        PaymentService paymentService = mock(PaymentService.class);
        BookingEventConsumer consumer = new BookingEventConsumer(new ObjectMapper(), paymentService);

        String payload = """
            {
              "schemaVersion":"1.0",
              "eventId":"event-1",
              "type":"BOOKING_DRAFT_CREATED",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222",
              "priceTotal":400000.00
            }
            """;

        consumer.consumeBookingEvent(payload, "booking.events", null);

        verify(paymentService, times(1)).initiateDepositForBookingEvent(
            eq(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")),
            eq(java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")),
            argThat(amount -> amount.compareTo(new java.math.BigDecimal("400000.00")) == 0),
            eq(null),
            eq("event-1")
        );
    }

    @Test
    void consumeBookingEvent_shouldPropagateEmailFromSchemaVersionOnePointOne() {
        PaymentService paymentService = mock(PaymentService.class);
        BookingEventConsumer consumer = new BookingEventConsumer(new ObjectMapper(), paymentService);

        String payload = """
            {
              "schemaVersion":"1.1",
              "eventId":"event-1-1",
              "type":"BOOKING_DRAFT_CREATED",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222",
              "customerEmail":"customer@example.com",
              "priceTotal":400000.00
            }
            """;

        consumer.consumeBookingEvent(payload, "booking.events", null);

        verify(paymentService).initiateDepositForBookingEvent(
            eq(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")),
            eq(java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")),
            argThat(amount -> amount.compareTo(new java.math.BigDecimal("400000.00")) == 0),
            eq("customer@example.com"),
            eq("event-1-1")
        );
    }

    @Test
    void consumeBookingEvent_shouldRejectUnsupportedSchemaVersion() {
        PaymentService paymentService = mock(PaymentService.class);
        BookingEventConsumer consumer = new BookingEventConsumer(new ObjectMapper(), paymentService);

        String payload = """
            {
              "schemaVersion":"2.0",
              "eventId":"event-2",
              "type":"BOOKING_DRAFT_CREATED",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222",
              "priceTotal":400000.00
            }
            """;

        assertThatThrownBy(() -> consumer.consumeBookingEvent(payload, "booking.events", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported booking event schemaVersion");

        verifyNoInteractions(paymentService);
    }
}
