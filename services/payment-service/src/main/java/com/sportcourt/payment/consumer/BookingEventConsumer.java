package com.sportcourt.payment.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);
    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    public BookingEventConsumer(ObjectMapper objectMapper, PaymentService paymentService) {
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
    }

    @KafkaListener(
        topics = "${kafka.topics.booking-events}",
        groupId = "${kafka.consumer.booking.group-id:payment-service-booking}",
        autoStartup = "${kafka.consumer.booking.enabled:true}"
    )
    public void consumeBookingEvent(@Payload String payload,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(name = "event-id", required = false) String headerEventId) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            validateSchemaVersion(event.path("schemaVersion").asText(null));
            String type = event.path("type").asText();
            if (!"BOOKING_DRAFT_CREATED".equals(type)) {
                return;
            }

            String eventId = readNonBlank(event.path("eventId").asText(null), headerEventId);
            if (eventId == null) {
                log.warn("Skip booking event without eventId in topic {}", topic);
                return;
            }

            UUID bookingId = UUID.fromString(event.path("bookingId").asText());
            UUID customerId = UUID.fromString(event.path("customerId").asText());
            BigDecimal priceTotal = event.path("priceTotal").decimalValue();

            paymentService.initiateDepositForBookingEvent(bookingId, customerId, priceTotal, eventId);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process booking event", ex);
        }
    }

    private String readNonBlank(String valueFromPayload, String valueFromHeader) {
        if (valueFromPayload != null && !valueFromPayload.isBlank()) {
            return valueFromPayload;
        }
        if (valueFromHeader != null && !valueFromHeader.isBlank()) {
            return valueFromHeader;
        }
        return null;
    }

    private void validateSchemaVersion(String schemaVersion) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            return;
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported booking event schemaVersion: " + schemaVersion);
        }
    }
}
