package com.sportcourt.reporting.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.reporting.service.BookingProjectionService;
import com.sportcourt.reporting.service.ProjectionEventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class BookingEventConsumer {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final ProjectionEventService projectionEventService;
    private final BookingProjectionService bookingProjectionService;

    public BookingEventConsumer(ObjectMapper objectMapper,
                                ProjectionEventService projectionEventService,
                                BookingProjectionService bookingProjectionService) {
        this.objectMapper = objectMapper;
        this.projectionEventService = projectionEventService;
        this.bookingProjectionService = bookingProjectionService;
    }

    @KafkaListener(
        topics = "${kafka.topics.booking-events}",
        groupId = "${kafka.consumer.booking.group-id}",
        autoStartup = "${kafka.consumer.booking.enabled:true}"
    )
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(name = "event-id", required = false) String headerEventId) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            validateSchemaVersion(event.path("schemaVersion").asText(null));
            String eventId = readNonBlank(event.path("eventId").asText(null), headerEventId);
            if (!projectionEventService.reserve(eventId, topic)) {
                return;
            }

            UUID bookingId = parseUuid(event.path("bookingId").asText(null));
            if (bookingId == null) {
                return;
            }
            bookingProjectionService.projectBookingSnapshot(
                bookingId,
                parseUuid(event.path("venueId").asText(null)),
                parseUuid(event.path("courtId").asText(null)),
                parseUuid(event.path("customerId").asText(null)),
                textOrNull(event, "status"),
                textOrNull(event, "paymentStatus"),
                parseOffsetDateTime(event.path("startTime").asText(null)),
                parseOffsetDateTime(event.path("endTime").asText(null)),
                parseBigDecimal(event.path("priceTotal").asText(null)),
                textOrNull(event, "type"),
                parseOffsetDateTime(event.path("occurredAt").asText(null))
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to project booking event", ex);
        }
    }

    private void validateSchemaVersion(String schemaVersion) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            return;
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported booking event schemaVersion: " + schemaVersion);
        }
    }

    private String readNonBlank(String payloadValue, String headerValue) {
        if (payloadValue != null && !payloadValue.isBlank()) {
            return payloadValue;
        }
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return UUID.fromString(value);
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return new BigDecimal(value);
    }
}
