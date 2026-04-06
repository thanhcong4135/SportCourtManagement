package com.sportcourt.payment.consumer;

import com.sportcourt.payment.dlq.DeadLetterEventService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BookingEventDlqConsumerTest {

    @Test
    void consume_shouldForwardMessageToDeadLetterService() {
        DeadLetterEventService deadLetterEventService = mock(DeadLetterEventService.class);
        BookingEventDlqConsumer consumer = new BookingEventDlqConsumer(deadLetterEventService);

        Map<String, Object> headers = new HashMap<>();
        headers.put("event-id", "evt-1");

        consumer.consume("{\"eventId\":\"evt-1\"}", "booking.events.dlq", 0, 15L, headers);

        verify(deadLetterEventService, times(1))
            .capture(eq("booking.events.dlq"), eq(0), eq(15L), eq("{\"eventId\":\"evt-1\"}"), any());
    }
}
