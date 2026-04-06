package com.sportcourt.core.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.core.service.PaymentEventProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentEventConsumerTest {

    @Test
    void consumePaymentEvent_shouldProcessWhenSchemaVersionSupported() {
        PaymentEventProcessor processor = mock(PaymentEventProcessor.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(new ObjectMapper(), processor);

        String payload = """
            {
              "schemaVersion":"1.0",
              "eventId":"11111111-1111-1111-1111-111111111111",
              "type":"DEPOSIT_SUCCEEDED",
              "paymentId":"22222222-2222-2222-2222-222222222222",
              "bookingId":"33333333-3333-3333-3333-333333333333",
              "customerId":"44444444-4444-4444-4444-444444444444",
              "amount":200000.00
            }
            """;

        consumer.consumePaymentEvent(payload, "payment.events", null);

        verify(processor, times(1)).process(any(), eq("payment.events"), eq("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void consumePaymentEvent_shouldRejectUnsupportedSchemaVersion() {
        PaymentEventProcessor processor = mock(PaymentEventProcessor.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(new ObjectMapper(), processor);

        String payload = """
            {
              "schemaVersion":"2.0",
              "eventId":"11111111-1111-1111-1111-111111111111",
              "type":"DEPOSIT_SUCCEEDED",
              "paymentId":"22222222-2222-2222-2222-222222222222",
              "bookingId":"33333333-3333-3333-3333-333333333333"
            }
            """;

        assertThatThrownBy(() -> consumer.consumePaymentEvent(payload, "payment.events", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported payment event schemaVersion");

        verifyNoInteractions(processor);
    }
}
