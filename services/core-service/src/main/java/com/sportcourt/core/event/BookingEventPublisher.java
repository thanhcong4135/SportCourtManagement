package com.sportcourt.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.core.domain.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public BookingEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${kafka.topics.booking-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(BookingEventType type, Booking booking) {
        BookingEvent event = new BookingEvent();
        event.setSchemaVersion("1.1");
        event.setEventId(UUID.randomUUID());
        event.setType(type);
        event.setBookingId(booking.getId());
        event.setCourtId(booking.getCourt() != null ? booking.getCourt().getId() : null);
        event.setCustomerId(booking.getCustomerId());
        event.setCustomerEmail(booking.getCustomerEmail());
        event.setStatus(booking.getStatus());
        event.setPaymentStatus(booking.getPaymentStatus());
        event.setStartTime(booking.getStartTime());
        event.setEndTime(booking.getEndTime());
        event.setPriceTotal(booking.getPriceTotal());
        event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));

        try {
            String payload = objectMapper.writeValueAsString(event);
            publishRaw(topic, booking.getId().toString(), payload, event.getEventId().toString());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize booking event {}", booking.getId(), e);
        }
    }

    public void publishRaw(String topicName, String key, String payload) {
        publishRaw(topicName, key, payload, null);
    }

    public void publishRaw(String topicName, String key, String payload, String eventId) {
        try {
            var messageBuilder = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, topicName)
                .setHeader(KafkaHeaders.KEY, key);
            if (eventId != null && !eventId.isBlank()) {
                messageBuilder.setHeader("event-id", eventId);
            }
            kafkaTemplate.send(messageBuilder.build()).join();
        } catch (CompletionException e) {
            throw new IllegalStateException("Failed to publish event to Kafka", e);
        }
    }
}
