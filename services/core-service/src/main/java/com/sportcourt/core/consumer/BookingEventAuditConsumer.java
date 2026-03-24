package com.sportcourt.core.consumer;

import com.sportcourt.core.service.IdempotentEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class BookingEventAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventAuditConsumer.class);

    private final IdempotentEventService idempotentEventService;

    public BookingEventAuditConsumer(IdempotentEventService idempotentEventService) {
        this.idempotentEventService = idempotentEventService;
    }

    @KafkaListener(
        topics = "${kafka.topics.booking-events}",
        groupId = "${kafka.consumer.audit.group-id:core-service-audit}",
        autoStartup = "${kafka.consumer.audit.enabled:false}"
    )
    public void onBookingEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(name = "event-id", required = false) String eventId
    ) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Skip booking event without event-id header on topic {}", topic);
            return;
        }

        boolean firstDelivery = idempotentEventService.tryMarkProcessed(eventId, topic);
        if (!firstDelivery) {
            log.debug("Skip duplicate event {}", eventId);
            return;
        }

        log.debug("Received new booking event {} with payload size {}", eventId, payload.length());
    }
}
