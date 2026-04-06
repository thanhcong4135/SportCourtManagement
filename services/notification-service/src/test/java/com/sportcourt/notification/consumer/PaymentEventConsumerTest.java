package com.sportcourt.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.notification.service.NotificationEventFactory;
import com.sportcourt.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentEventConsumerTest {

    @Test
    void consume_shouldQueueNotificationWhenPayloadValid() {
        NotificationService notificationService = mock(NotificationService.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(
            new ObjectMapper(),
            new NotificationEventFactory(),
            notificationService
        );

        String payload = """
            {
              "schemaVersion":"1.0",
              "eventId":"evt-2",
              "type":"DEPOSIT_SUCCEEDED",
              "paymentId":"33333333-3333-3333-3333-333333333333",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222"
            }
            """;

        consumer.consume(payload, "payment.events", null);

        verify(notificationService, times(1)).queueFromEvent(any());
    }

    @Test
    void consume_shouldRejectUnsupportedSchemaVersion() {
        NotificationService notificationService = mock(NotificationService.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(
            new ObjectMapper(),
            new NotificationEventFactory(),
            notificationService
        );

        String payload = """
            {
              "schemaVersion":"2.0",
              "eventId":"evt-2",
              "type":"DEPOSIT_SUCCEEDED",
              "paymentId":"33333333-3333-3333-3333-333333333333",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222"
            }
            """;

        assertThatThrownBy(() -> consumer.consume(payload, "payment.events", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported payment event schemaVersion");

        verifyNoInteractions(notificationService);
    }
}
