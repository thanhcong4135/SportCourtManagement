package com.sportcourt.payment.consumer;

import com.sportcourt.payment.dlq.DeadLetterEventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BookingEventDlqConsumer {

    private final DeadLetterEventService deadLetterEventService;

    public BookingEventDlqConsumer(DeadLetterEventService deadLetterEventService) {
        this.deadLetterEventService = deadLetterEventService;
    }

    @KafkaListener(
        topics = "${kafka.topics.booking-events-dlq}",
        groupId = "${kafka.consumer.booking-dlq.group-id:payment-service-booking-dlq}",
        autoStartup = "${kafka.consumer.booking-dlq.enabled:true}"
    )
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String deadLetterTopic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        @Headers Map<String, Object> headers) {
        deadLetterEventService.capture(deadLetterTopic, partition, offset, payload, headers);
    }
}
