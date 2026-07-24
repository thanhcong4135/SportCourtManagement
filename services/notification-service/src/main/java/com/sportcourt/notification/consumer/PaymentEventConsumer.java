package com.sportcourt.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.notification.service.NotificationEventFactory;
import com.sportcourt.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationEventFactory notificationEventFactory;
    private final NotificationService notificationService;

    public PaymentEventConsumer(ObjectMapper objectMapper,
                                NotificationEventFactory notificationEventFactory,
                                NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationEventFactory = notificationEventFactory;
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${kafka.topics.payment-events}",
        groupId = "${kafka.consumer.payment.group-id:notification-service-payment}",
        autoStartup = "${kafka.consumer.payment.enabled:true}"
    )
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(name = "event-id", required = false) String headerEventId) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            notificationEventFactory.fromPaymentEvent(event, topic, headerEventId)
                .forEach(notificationService::queueFromEvent);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process payment event", ex);
        }
    }
}
