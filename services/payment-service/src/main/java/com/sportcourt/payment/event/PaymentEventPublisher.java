package com.sportcourt.payment.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishRaw(String topic, String key, String payload, String eventId) {
        var message = MessageBuilder.withPayload(payload)
            .setHeader(KafkaHeaders.TOPIC, topic)
            .setHeader(KafkaHeaders.KEY, key)
            .setHeader("event-id", eventId)
            .build();
        kafkaTemplate.send(message).join();
    }
}
