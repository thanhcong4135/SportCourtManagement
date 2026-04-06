package com.sportcourt.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sportcourt.notification.domain.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationEventFactory {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";

    public NotificationEventCommand fromBookingEvent(JsonNode event, String topic, String headerEventId) {
        validateSchemaVersion(event.path("schemaVersion").asText(null), "booking");

        String type = event.path("type").asText();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Booking event missing type");
        }
        if (!isSupportedBookingType(type)) {
            return null;
        }

        UUID bookingId = readUuid(event, "bookingId", true);
        UUID customerId = readUuid(event, "customerId", true);
        String eventId = readEventId(event, headerEventId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "booking.events");
        metadata.put("bookingId", bookingId.toString());
        putIfNotBlank(metadata, "courtId", event.path("courtId").asText(null));
        putIfNotBlank(metadata, "status", event.path("status").asText(null));
        putIfNotBlank(metadata, "paymentStatus", event.path("paymentStatus").asText(null));
        putIfNotBlank(metadata, "startTime", event.path("startTime").asText(null));
        putIfNotBlank(metadata, "endTime", event.path("endTime").asText(null));

        String templateCode = type;
        String message = switch (type) {
            case "BOOKING_DRAFT_CREATED" -> "Booking draft has been created and is waiting for confirmation.";
            case "BOOKING_CONFIRMED" -> "Booking has been confirmed successfully.";
            case "BOOKING_CANCELED" -> "Booking has been canceled.";
            case "BOOKING_IN_PROGRESS" -> "Booking is now in progress.";
            case "BOOKING_COMPLETED" -> "Booking has been completed.";
            case "BOOKING_DEPOSITED" -> "Deposit has been received for your booking.";
            case "BOOKING_PAYMENT_FAILED" -> "Payment for your booking failed. Please retry.";
            default -> "Booking status has been updated.";
        };

        return new NotificationEventCommand(
            topic,
            eventId,
            type,
            bookingId,
            null,
            customerId,
            eventId,
            NotificationChannel.IN_APP,
            customerId.toString(),
            templateCode,
            message,
            metadata
        );
    }

    public NotificationEventCommand fromPaymentEvent(JsonNode event, String topic, String headerEventId) {
        validateSchemaVersion(event.path("schemaVersion").asText(null), "payment");

        String type = event.path("type").asText();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Payment event missing type");
        }
        if (!isSupportedPaymentType(type)) {
            return null;
        }

        UUID paymentId = readUuid(event, "paymentId", true);
        UUID bookingId = readUuid(event, "bookingId", true);
        UUID customerId = readUuid(event, "customerId", false);
        String eventId = readEventId(event, headerEventId);

        String recipient = customerId != null ? customerId.toString() : bookingId.toString();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "payment.events");
        metadata.put("paymentId", paymentId.toString());
        metadata.put("bookingId", bookingId.toString());
        putIfNotBlank(metadata, "providerReference", event.path("providerReference").asText(null));
        if (customerId != null) {
            metadata.put("customerId", customerId.toString());
        }

        String templateCode = type;
        String message = switch (type) {
            case "DEPOSIT_SUCCEEDED" -> "Deposit payment succeeded.";
            case "DEPOSIT_FAILED" -> "Deposit payment failed. Please retry.";
            default -> "Payment status has been updated.";
        };

        return new NotificationEventCommand(
            topic,
            eventId,
            type,
            bookingId,
            paymentId,
            customerId,
            eventId,
            NotificationChannel.IN_APP,
            recipient,
            templateCode,
            message,
            metadata
        );
    }

    private void validateSchemaVersion(String schemaVersion, String domain) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            return;
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported " + domain + " event schemaVersion: " + schemaVersion);
        }
    }

    private boolean isSupportedBookingType(String type) {
        return "BOOKING_DRAFT_CREATED".equals(type)
            || "BOOKING_CONFIRMED".equals(type)
            || "BOOKING_CANCELED".equals(type)
            || "BOOKING_IN_PROGRESS".equals(type)
            || "BOOKING_COMPLETED".equals(type)
            || "BOOKING_DEPOSITED".equals(type)
            || "BOOKING_PAYMENT_FAILED".equals(type);
    }

    private boolean isSupportedPaymentType(String type) {
        return "DEPOSIT_SUCCEEDED".equals(type) || "DEPOSIT_FAILED".equals(type);
    }

    private UUID readUuid(JsonNode event, String field, boolean required) {
        String value = event.path(field).asText(null);
        if (value == null || value.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Event missing " + field);
            }
            return null;
        }
        return UUID.fromString(value);
    }

    private String readEventId(JsonNode event, String headerEventId) {
        String eventId = event.path("eventId").asText(null);
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        if (headerEventId != null && !headerEventId.isBlank()) {
            return headerEventId;
        }
        throw new IllegalArgumentException("Event missing eventId");
    }

    private void putIfNotBlank(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }
}
