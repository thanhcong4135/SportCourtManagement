package com.sportcourt.core.consumer;

import com.sportcourt.core.dlq.DeadLetterEventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentEventDlqConsumer {

    private final DeadLetterEventService deadLetterEventService;

    public PaymentEventDlqConsumer(DeadLetterEventService deadLetterEventService) {
        this.deadLetterEventService = deadLetterEventService;
    }

    @KafkaListener(
        topics = "${kafka.topics.payment-events-dlq}",
        groupId = "${kafka.consumer.payment-dlq.group-id:core-service-payment-dlq}",
        autoStartup = "${kafka.consumer.payment-dlq.enabled:true}"
    )
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String deadLetterTopic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        @Headers Map<String, Object> headers) {
        deadLetterEventService.capture(deadLetterTopic, partition, offset, payload, headers);
    }
}
