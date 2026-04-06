package com.sportcourt.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.notification.service.NotificationEventCommand;
import com.sportcourt.notification.service.NotificationEventFactory;
import com.sportcourt.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationEventFactory notificationEventFactory;
    private final NotificationService notificationService;

    public BookingEventConsumer(ObjectMapper objectMapper,
                                NotificationEventFactory notificationEventFactory,
                                NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationEventFactory = notificationEventFactory;
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${kafka.topics.booking-events}",
        groupId = "${kafka.consumer.booking.group-id:notification-service-booking}",
        autoStartup = "${kafka.consumer.booking.enabled:true}"
    )
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(name = "event-id", required = false) String headerEventId) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            NotificationEventCommand command = notificationEventFactory.fromBookingEvent(event, topic, headerEventId);
            if (command != null) {
                notificationService.queueFromEvent(command);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process booking event", ex);
        }
    }
}
